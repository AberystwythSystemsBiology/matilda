;; Copyright (C) 2019 Rob Bolton

;; This file is part of MATILDA.

;; MATILDA is free software: you can redistribute it and/or modify
;; it under the terms of the GNU General Public License (version 3)
;; as published by the Free Software Foundation.

;; MATILDA is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU General Public License for more details.

;; You should have received a copy of the GNU General Public License
;; along with MATILDA. If not, see <https://www.gnu.org/licenses/>.

;; Additional permission under GNU GPL version 3 section 7

;; If you modify MATILDA, or any covered work, by linking or
;; combining it with any of the libraries listed in the DEPENDENCIES
;; file (or a modified version of those libraries), containing parts
;; covered by the terms of their respective licenses, the licensors
;; of MATILDA grant you additional permission to convey the resulting work.
;; Corresponding Source for a non-source form of such a combination
;; shall include the source code for the parts of the linked or combined
;; libraries used as well as that of the covered work.

;; The DEPENDENCIES file is distributed with MATILDA.


(ns matilda.db
  (:gen-class)
  (:import [org.apache.jena.tdb TDBFactory]
           [org.apache.jena.query Dataset ReadWrite]
           [org.apache.jena.query QueryFactory QueryExecutionFactory ResultSetFormatter]
           [org.apache.jena.sparql.core Var]
           [org.apache.jena.rdf.model ResourceFactory]
           [java.io ByteArrayOutputStream])
  (:require [mount.core :refer [defstate]]
            [matilda.config :refer [ConfMgr]]
            [omniconf.core :as cfg]
            [cheshire.core :refer [parse-string]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))


(def DbCon)

(defn connect-tdb
  []
  (let [dir (cfg/get :tdb-dir)]
    (log/info "Initializing tdb " dir)
    (TDBFactory/createDataset dir)))

(defn disconnect-tdb
  []
  (.close DbCon)
  (TDBFactory/release DbCon)
  nil)

(defstate DbCon :start (connect-tdb)
                :stop (disconnect-tdb))

(defmacro with-dataset
  [ds readwrite body]
  `(try
    (do
      (.begin ~ds ~readwrite)
      (let [result# ~body]
        (when (= ~readwrite ReadWrite/WRITE)
          (.commit ~ds))
        (.end ~ds)
        result#))
    (catch Exception e#
      (try (.end ~ds)
           (catch Exception e2# (throw e#))))))

(defn create-literal
  [v]
  (ResourceFactory/createTypedLiteral v))

(defn create-property
  [uri]
  (ResourceFactory/createProperty uri))

(defn create-resource
  [uri]
  (ResourceFactory/createResource uri))

(defn result->map
  [result]
  (let [binding (.getBinding result)
        vars (.vars binding)]
    (into
     {}
     (map #(vector (str %1) (str (Var/lookup binding %1)))
          (iterator-seq vars)))))

(defn iri-to-parts
  [iri]
  (let [hash-parts (str/split iri #"#")
        slash-parts (str/split iri #"/")]
    (if (> (count hash-parts) 1)
      [(str (str/join "#" (butlast hash-parts)) "#")
       (last hash-parts)]
      [(str (str/join "/" (butlast slash-parts)) "/")
       (last slash-parts)])))

(defn node->value
  [node]
  (cond
    (.isLiteral node) (.getValue (.getLiteral node))
    (.isURI node) (.getURI node)
     :else (str node)))

(defn result->map
  [result]
  (let [binding (.getBinding result)
        vars (.vars binding)]
    (into {} (map #(vector (str %1) (node->value (Var/lookup binding %1)))
                  (iterator-seq vars)))))

(defn coalesce-query-results
  [id results]
  (let [setmaps (map (partial reduce-kv
                              (fn [m k v]
                                (assoc m k (set [v])))
                              {})
                     results)
        groups (group-by (fn [row] (first (get row id))) setmaps)]
    (apply hash-map
           (flatten (map (fn [[id rows]]
                           [id (apply (partial merge-with clojure.set/union) rows)])
                         groups)))))

(defn group-query-results
  [id results]
  (group-by #(get %1 id) results))

(defn query-db-raw
  [query-str]
  (with-dataset DbCon ReadWrite/READ
    (let [model (.getNamedModel DbCon "urn:x-arq:UnionGraph")
          query (QueryFactory/create query-str)
          query-exec (QueryExecutionFactory/create query model)
          results (iterator-seq (.execSelect query-exec))]
      results)))

(defn query-db
  ([query-str graph-name]
   (with-dataset DbCon ReadWrite/READ
     (let [model (.getNamedModel DbCon graph-name)
           query (QueryFactory/create query-str)
           query-exec (QueryExecutionFactory/create query model)
           results (iterator-seq (.execSelect query-exec))
           mapped-results (map result->map results)]
       (doall mapped-results))))
  ([query-str]
   (query-db query-str "urn:x-arq:UnionGraph")))

(defn query-db-as-json
  [query-str]
  (with-dataset DbCon ReadWrite/READ
    (let [model (.getNamedModel DbCon "urn:x-arq:UnionGraph")
          query (QueryFactory/create query-str)
          query-exec (QueryExecutionFactory/create query model)
          res (.execSelect query-exec)
          os (ByteArrayOutputStream.)]
      (ResultSetFormatter/outputAsJSON os res)
      (parse-string (String. (.toByteArray os))))))

(defn query-db-coalesce
  [id query-str]
  (coalesce-query-results id (query-db query-str)))

(defn query-db-group
  [id query-str]
  (group-query-results id (query-db query-str)))

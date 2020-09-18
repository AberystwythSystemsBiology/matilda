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


(ns matilda.term
  (:gen-class)
  (:import [SymSpell SymSpell]
           [org.apache.jena.query ReadWrite QueryFactory QueryExecutionFactory])
  (:require [clojure.data.csv :as csv]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [clojure.java.io :as io]
            [omniconf.core :as cfg]
            [matilda.config :refer [ConfMgr]]
            [matilda.db :refer [DbCon with-dataset result->map]]
            [matilda.queries :refer [query-data make-query]]))


(def root-drug "http://purl.bioontology.org/ontology/SNOMEDCT/105590001")
(def root-condition "http://purl.bioontology.org/ontology/SNOMEDCT/404684003")
(def root-finding "http://purl.bioontology.org/ontology/SNOMEDCT/has_finding_site")

(defn with-seqs [f dst q] 
  (loop [xs q] 
    (let [[hd tl] (split-at dst xs)]
      (when-not (empty? hd)
        (f hd)
        (recur tl)))))

(defn result->csv-row
  "Turns a single row of a label query into a vector for CSV output"
  [{iri "?iri" label "?label"}]
    [iri (.toLowerCase label)])

(defn get-and-write-terms
  [out-file]
  (with-open [out (io/writer out-file)]
    (with-dataset DbCon ReadWrite/READ
      (let [model (.getDefaultModel DbCon)
            query-str (make-query {}
                                  "{ ?iri rdfs:label ?label } "
                                  "UNION"
                                  "{ ?iri skos:prefLabel ?label }"
                                  "UNION"
                                  "{ ?iri skos:altLabel ?label }")
            query (QueryFactory/create query-str)
            query-exec (QueryExecutionFactory/create query model)
            results (iterator-seq (.execSelect query-exec))]
        (as-> results r
          (map result->map r)
          (map result->csv-row r)
          (csv/write-csv out r :separator \tab))))))

(defn symspell-from-jdbc
  [db-spec]
  (reduce
   (fn [symspell {:keys [label]}]
     (.createDictionaryEntry symspell label 1 nil)
     symspell)
   (SymSpell. -1 2 -1 1)
   (jdbc/reducible-query
    db-spec
    ["SELECT label FROM terms"]
    {:raw? true})))

;; db-s {:dbtype "sqlite" :dbname "/home/rob/matilda-dirs/clj/terms/terms.sqlite"}
(defn get-and-write-terms-db!
  [db-spec]
  (with-dataset DbCon ReadWrite/READ
    (let [model (.getDefaultModel DbCon)
          query-str (make-query {}
                                "{ ?iri rdfs:label ?label } "
                                "UNION"
                                "{ ?iri skos:prefLabel ?label }"
                                "UNION"
                                "{ ?iri skos:altLabel ?label }")
          query (QueryFactory/create query-str)
          query-exec (QueryExecutionFactory/create query model)
          results (iterator-seq (.execSelect query-exec))]
      (jdbc/db-do-commands db-spec
       ["CREATE VIRTUAL TABLE terms USING fts5
         (uri, label, tokenize='porter ascii')"])
      (as-> results r
        (map result->map r)
        (map result->csv-row r)
        (with-seqs #(jdbc/insert-multi! db-spec :terms nil %1) 10000 r)))))

(defn term-table-exists?
  [db-spec]
  (seq (jdbc/query db-spec ["SELECT name FROM sqlite_master WHERE type='table' AND name='terms'"])))

(defn load-or-make-terms
  [db-spec]
  (when-not (term-table-exists? db-spec)
    (get-and-write-terms-db! db-spec))
  (symspell-from-jdbc db-spec))

(def TermSearcher)

(defn term-file
  [file-name]
  (format "%s/%s.json" (cfg/get :term-dir) file-name))

(defn init-terms
  []
  (let [symspell (load-or-make-terms (cfg/get :jdbc))]
    {:symspell symspell}))

(defn deinit-terms
  []
  nil)

(defstate TermSearcher :start (init-terms)
                       :stop (deinit-terms))

(defn query-terms
  [db-spec term]
  (let [matches (jdbc/query db-spec ["SELECT uri, label FROM terms
                                      WHERE label MATCH ?
                                      ORDER BY length(label)" term])]
    matches))

(defn term->uri
  [db-spec term]
  (:uri (first (jdbc/query db-spec ["SELECT uri FROM terms WHERE label = ?" term]))))

(defn sym-term->term-pair
  [db-spec term-obj]
  (let [term (.term term-obj)]
    {:uri (term->uri db-spec term) :label term}))

(defn search-terms
  [term]
  (let [dict-res (.lookup (:symspell TermSearcher) term SymSpell.SymSpell$Verbosity/All)
        sym-terms (map #(sym-term->term-pair (cfg/get :jdbc) %1) dict-res)
        queried-items (query-terms (cfg/get :jdbc) term)
        terms (distinct (concat sym-terms queried-items))]
    terms))

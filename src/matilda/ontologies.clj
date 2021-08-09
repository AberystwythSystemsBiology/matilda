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

(ns matilda.ontologies
  (:gen-class)
  (:import [org.apache.jena.query Dataset ReadWrite])
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [matilda.config :refer [ConfMgr]]
            [matilda.queries :refer [make-query query-data]]
            [matilda.db :refer [DbCon with-dataset query-db]]
            [matilda.util :refer [mk-matilda-term]])
  (:import [org.apache.jena.vocabulary RDF DC]))


(def ont-graph-name "MATILDA:Ontologies")

(defn mk-ont-iri [id] (format "%s%s" (cfg/get :matilda-ontologies-root) id))

(defn ontology-type-iri [] (mk-matilda-term "NamedOntology"))
(defn ontology-has-named-iri [] (mk-matilda-term "hasIri"))

(defn query-ontologies
  []
  (let [qstr  (make-query {}
                          (format "?ont rdf:type <%s> ." (ontology-type-iri))
                          "?ont dc:identifier ?id ."
                          "?ont dc:title ?title ."
                          "?ont dc:description ?desc")]
    (query-data qstr ont-graph-name)))

(defn query-ontology-iris
  [id]
  (let [qstr  (make-query {} (format "<%s> <%s> ?iri"
                                     (mk-ont-iri id)
                                     (ontology-has-named-iri)))]
    (query-data qstr ont-graph-name)))

(defn load-ontology-url
  [url]
  (with-dataset DbCon ReadWrite/WRITE
    (let [model (.getNamedModel DbCon url)]
      (.read model url)
      url)))

(defn load-ontology-file
  [file-name]
  (let [tmp-name (str "matilda:temp-" (rand))]
    (try
      (with-open [r (io/input-stream file-name)]
        (with-dataset DbCon ReadWrite/WRITE
          (do (-> DbCon
                  (.getNamedModel tmp-name)
                  (.read r nil))
              nil)))
      (let [res (query-db (make-query {} "?url rdf:type owl:Ontology")
                          tmp-name)
            base (get (first res) "?url")]
        (when base
          (with-dataset DbCon ReadWrite/WRITE
            (.addNamedModel DbCon base (.getNamedModel DbCon tmp-name))))
        base)
      (finally
        (with-dataset DbCon ReadWrite/WRITE (.removeNamedModel DbCon tmp-name))))))

(defn list-ontologies
  []
  (let [ontologies (query-ontologies)]
    (map (fn [{ont "?ont" id "?id" title "?title" desc "?desc"}]
           {:ont ont :id id :title title :desc desc})
         ontologies)))

(defn list-ontology-iris
  [id]
  (let [iris (query-ontology-iris id)]
    (map #(get % "?iri") iris)))

;; TODO: Global ontology: Check once at startup, load if not in named graphs
;;       Contains terminology for e.g. graph metadata management, rawfile definitions
;;
;; in metagraph: ontology ID
;; under ID graph: import ontology files
;;
;; Staging:
;; + Take files to tmp
;; + Submit ID & Go then:
;;   + Create ID-named graph, read each file into graph
;;   + SPARQL ID-named graph for meta
;;   + Foreach (?s rdf:type Ontology) in ID-named graph
;;     insert a (<url> matilda:hasID \"ID\") into meta graph

;; Get graph URLs from (concat (map load-file files) urls)
;; Add named graphs to metagraph
(defn create-ontology
  "Add a new set of metadata about a named ontology grouping"
  [id title description]
  (with-dataset DbCon ReadWrite/WRITE
    (let [g-meta (.getNamedModel DbCon ont-graph-name)
          ont-res (.createResource g-meta (mk-ont-iri id))
          dataset-res (.createResource g-meta (ontology-type-iri))]
      (.add g-meta (.createStatement g-meta ont-res RDF/type dataset-res))
      (.add g-meta (.createStatement g-meta ont-res DC/identifier id))
      (.add g-meta (.createStatement g-meta ont-res DC/title title))
      (.add g-meta (.createStatement g-meta ont-res DC/description description))
      id)))

(defn add-to-ontology
  "Add the specified files or URLs to the named ontology grouping, loading each to their own named graph"
  [id files urls]
  (let [named-iris (->> (concat (map load-ontology-file files)
                                (map load-ontology-url urls))
                        (filter some?)
                        doall)]
    (with-dataset DbCon ReadWrite/WRITE
      (let [g-meta (.getNamedModel DbCon ont-graph-name)
            ont-res (.getResource g-meta (mk-ont-iri id))
            has-named (.createProperty g-meta (ontology-has-named-iri))]
        (doall (map (fn [iri] (.add g-meta (.createStatement g-meta ont-res has-named iri))) named-iris))
        named-iris))))

(defn delete-named-model
  "Delete a named Model from a TDB Dataset"
  [url]
  (with-dataset DbCon ReadWrite/WRITE
    (when (.containsNamedModel DbCon url)
      (.removeNamedModel DbCon url)
      url)))

;; TODO: Use set difference to ensure we only delete orphaned deps and not mutual deps
;; i.e. (set/difference this/deps (map (get-deps) (list-onts)))
(defn delete-ontology
  "Delete an ontology grouping, including its imported ontologies"
  [id]
  (let [named-graphs (list-ontology-iris id)]
    (with-dataset DbCon ReadWrite/WRITE
      (let [g-meta (.getNamedModel DbCon ont-graph-name)
            ont-res (.getResource g-meta (mk-ont-iri id))]
        (doall (map (fn [iri] (.removeNamedModel DbCon iri)) named-graphs))
        (.removeAll g-meta ont-res nil nil)
        id))))

;; TODO:
;; /autoscan?url=<url>
;; WHERE:
;; + <url> shall be downloaded to <file>
;; + <file> shall be loaded into a temporary <model>
;; + <model> shall be scanned for metadata
;; + WHEN at least an Ontology node is found:
;;   + Load uri + [title, desc, version, [imports?]] to <meta>
;;   + ontologies/create-ontology with <meta>
;; Q: can I use the Ontology API with TDB?
;; A: "yes" (no)
;; Nb: http://mail-archives.apache.org/mod_mbox/jena-users/202107.mbox/%3c10C346A1-A35A-4585-9C7E-D70F500227A9@hum.ku.dk%3e

;; load-ontology-file optional base URL
;; WHEN {baseURL}
;;   Load file to graph named {baseURL}
;; WHEN NOT {baseURL}
;;   Load file to temp graph G
;;   Inspect temp graph G for Ontology base B
;;   Rename G -> B
;; FINALLY
;;   Extract meta from final named graph {baseURL} OR {B}
;;   Insert into matilda:graphMeta tuples for each Meta
;;   Resolve imports (i.e. recurse)

;; Load file to temp graph
;; Inspect for Ontology meta
;; Rename 
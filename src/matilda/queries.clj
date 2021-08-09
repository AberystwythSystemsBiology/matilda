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


(ns matilda.queries
  (:gen-class)
  (:require [omniconf.core :as cfg]
            [clojure.string :refer [join]]
            [matilda.db :refer [query-db 
                                query-db-coalesce
                                query-db-group
                                query-db-as-json]]))

(defn to-prefix
  [[prefix url]]
  (format "PREFIX %s: <%s>" prefix url))

(defn make-query
  [prefixes
   & chonks]
  (join "\n"
    `("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
      "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
      "PREFIX owl: <http://www.w3.org/2002/07/owl#>"
      "PREFIX dc: <http://purl.org/dc/elements/1.1/>"
      "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"
      "PREFIX rxnorm: <http://purl.bioontology.org/ontology/RXNORM/>"
      "PREFIX snomed: <http://purl.bioontology.org/ontology/SNOMEDCT/>"
      "PREFIX obo: <http://purl.obolibrary.org/obo/>"
      "PREFIX oboInOwl: <http://www.geneontology.org/formats/oboInOwl#>"
      "PREFIX umls: <http://bioportal.bioontology.org/ontologies/umls/>"
      ~(format "PREFIX matilda: <%s>" (cfg/get :matilda-ont-root))
      ~@(map to-prefix prefixes)
      "SELECT * WHERE {"
      ~@chonks
      "}")))

(defn query-data
  ([query-str]
   (query-db query-str))
  ([query-str graph]
   (query-db query-str graph)))

(defn query-data-j
  [query-str]
  (query-db-as-json query-str))

(defn query-data-grouped
  [query-str group-key]
  (let [results (query-db-as-json query-str)
        grouped-bindings (group-by #(get-in %1 [group-key "value"])
                                   (get-in results ["results" "bindings"]))]
    (assoc-in results ["results" "bindings"] grouped-bindings)))

(defn describe-query
  [uri]
  (make-query {}
              "{"
                (format "{ <%s> rdfs:label ?label }" uri)
                "UNION"
                (format "{ <%s> skos:prefLabel ?label }" uri)
                "UNION"
                (format "{ <%s> skos:altLabel ?label }" uri)
              "}"
              "UNION"
              "{"
                (format "<%s> ?p ?o ." uri)
                "{ ?p rdfs:label ?pLabel }"
                "UNION"
                "{ ?p skos:prefLabel ?pLabel }"
                "UNION"
                "{ ?p skos:altLabel ?pLabel } ."

                "{ ?o rdfs:label ?oLabel }"
                "UNION"
                "{ ?o skos:prefLabel ?oLabel }"
                "UNION"
                "{ ?o skos:altLabel ?oLabel }"
              "}"
              "UNION"
              "{"
                (format "?s ?p <%s> ." uri)
                "{ ?p rdfs:label ?pLabel }"
                "UNION"
                "{ ?p skos:prefLabel ?pLabel }"
                "UNION"
                "{ ?p skos:altLabel ?pLabel } ."

                "{ ?s rdfs:label ?sLabel }"
                "UNION"
                "{ ?s skos:prefLabel ?sLabel }"
                "UNION"
                "{ ?s skos:altLabel ?sLabel }"
              "}"))

(defn description-type
  [dict]
  (cond
    (get dict "?s") :subject
    (get dict "?o") :object
    :else :label))

(defn reduce-labels
  [entries]
  (reduce 
   (fn [items item]
     (merge-with #(set (concat %1 %2))
                 (if (get item "?s")
                   {(get item "?s")
                    [(get item "?sLabel")]}
                   {})
                 (if (get item "?p")
                   {(get item "?p")
                    [(get item "?pLabel")]}
                   {})
                 (if (get item "?o")
                   {(get item "?o")
                    [(get item "?oLabel")]}
                   {})
                 items))
          {} entries))

(defn unique-stmts
  [stmts stmt-key]
  (set (map (fn [stmt] 
              [(get stmt "?p")
               (get stmt stmt-key)])
            stmts)))

(defn describe-item
  [uri]
  (let [stmts (query-data (describe-query uri))
        by-type (group-by description-type stmts)
        item-labels (map #(get %1 "?label") (:label by-type))
        annotation-labels (reduce-labels stmts)
        subj-stmts (unique-stmts (:subject by-type) "?s")
        obj-stmts (unique-stmts (:object by-type) "?o")]
    {:labels item-labels
     :subjects (into {} (map (fn [[pred subjs]]
                      [pred
                       {:label (get annotation-labels pred)
                        :subjects (map (fn [[pred subj]]
                                    {:iri subj
                                     :labels (get annotation-labels subj)})
                                       subjs)}])
                    (group-by first subj-stmts)))
     :objects (into {} (map (fn [[pred objs]]
                      [pred
                       {:label (get annotation-labels pred)
                        :objects (map (fn [[pred obj]]
                                    {:iri obj
                                     :labels (get annotation-labels obj)})
                                       objs)}])
                    (group-by first obj-stmts)))}))

(defn cui->uri
  [cui]
  (let [cui-res (query-data (make-query {} (format "?iri umls:cui \"%s\"" cui)))]
    (when cui-res
      (get (first cui-res) "?iri"))))

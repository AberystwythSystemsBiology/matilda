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

(ns matilda.data
  (:gen-class)
  (:import [org.apache.jena.query Dataset ReadWrite]
           [java.nio.file FileSystems])
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as str]
            [matilda.config :refer [ConfMgr]]
            [omniconf.core :as cfg]
            [matilda.db :refer [DbCon
                                with-dataset
                                create-literal
                                create-property
                                create-resource]]
            [matilda.util :refer [delete-dir! mk-path mk-matilda-term]]
            [matilda.queries :refer [make-query query-data]]
            [matilda.term :as term]))


(defn dataset-iri [] (mk-matilda-term "dataset"))
(defn id-iri [] (mk-matilda-term "id"))
(defn rawfile-iri [] (mk-matilda-term "raw"))


(def title-iri "http://purl.org/dc/elements/1.1/title")
(def description-iri "http://purl.org/dc/elements/1.1/description")
(def type-iri "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

(def subject-ont-iri "http://purl.bioontology.org/ontology/SNOMEDCT/116154003")
(def forename-iri "http://purl.obolibrary.org/obo/NCIT_C40974")
(def surname-iri "http://purl.obolibrary.org/obo/NCIT_C40975")
(def condition-iri "http://purl.bioontology.org/ontology/SNOMEDCT/417662000")
(def drug-iri "http://purl.bioontology.org/ontology/SNOMEDCT/438553004")
(def race-iri "http://purl.obolibrary.org/obo/NCIT_C17049")
(def age-iri "http://purl.obolibrary.org/obo/NCIT_C154631")
(def sex-iri "http://purl.obolibrary.org/obo/NCIT_C28421")

(defn dataset-root
  [id]
  (format "%s/%s" (cfg/get :matilda-dataset-root) id))

(defn dataset-exists?
  [id]
  (let [qstr (make-query {}
                         (format "?ont rdf:type <%s> ." (dataset-iri))
                         (format "?ont <%s> \"%s\"" id-iri id))
        res (doall (query-data qstr))]
    (not (empty? res))))

(defn query-datasets
  []
  (let [qstr  (make-query {}
                          (format "?ont rdf:type <%s> ." (dataset-iri))
                          "?ont dc:title ?title ."
                          "?ont dc:description ?desc ."
                          (format "?ont <%s> ?id" (id-iri)))]
    (query-data qstr)))

(defn dataset-rawfiles
  [id]
  (let [qstr (make-query {} 
                         (format "?ont rdf:type <%s> ." (dataset-iri))
                         (format "?ont <%s> \"%s\" ." (id-iri) id)
                         (format "?ont <%s> ?file" (rawfile-iri)))
        res (doall (query-data qstr))]
    (map #(get %1 "?file") res)))

(defn list-datasets
  []
  (let [datasets-res (query-datasets)]
    (map (fn [{desc "?desc" title "?title" id "?id"}]
           {:title title
            :description desc
            :id id
            :files (dataset-rawfiles id)})
         datasets-res)))

;; TODO: check dataset exists before create/delete etc.
;; Clean ID to contain in dir

(defn dataset-rawdir
  [id]
  (.toFile (mk-path [(cfg/get :data-dir) id])))

(defn create-dataset
  [id title description]
  (with-dataset DbCon ReadWrite/WRITE
    (let [root (dataset-root id)
          m (.getNamedModel DbCon (format "datasets/%s" id))
          res-dataset (.createResource m (format "%s#%s" root id))]
      (do
        (.add m (.createStatement m res-dataset
                                    (create-property type-iri)
                                    (create-resource (dataset-iri))))
        (.add m (.createStatement m res-dataset
                                    (create-property title-iri)
                                    (create-literal title)))
        (.add m (.createStatement m res-dataset
                                  (create-property description-iri)
                                  (create-literal description)))
        (.add m (.createStatement m res-dataset
                                  (create-property (id-iri))
                                  (create-literal id)))
        (.mkdir (dataset-rawdir id))
        nil))))

(defn get-dataset
  [id]
  (let [qstr (make-query {}
                         (format "?ont rdf:type <%s> ." (dataset-iri))
                         "?ont dc:title ?title ."
                         "?ont dc:description ?desc ."
                         (format "?ont <%s> \"%s\"" (id-iri) id))
        dataset (first (seq (query-data qstr)))]
    (when dataset
      {:id id
       :title (get dataset "?title")
       :description (get dataset "?desc")
       :files (dataset-rawfiles id)})))

(defn delete-dataset
  [id]
  (with-dataset DbCon ReadWrite/WRITE
    (.removeNamedModel DbCon (format "datasets/%s" id)))
  (delete-dir! (dataset-rawdir id))
  nil)

(defn add-raw-to-dataset
  [id raw-file]
  (io/copy raw-file (.toFile (mk-path [(dataset-rawdir id) (.getName raw-file)])))
  (with-dataset DbCon ReadWrite/WRITE
    (let [root (dataset-root id)
          m (.getNamedModel DbCon (format "datasets/%s" id))
          res-dataset (.createResource m (format "%s#%s" root id))]
      (.add m (.createStatement m res-dataset
                                (create-property (rawfile-iri))
                                (create-literal (.getName raw-file))))
      nil)))

(defn list-patients
  []
  (let [query-str (format "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                           SELECT * WHERE {
                             ?iri <%s> ?patientId .
                             ?iri <%s> ?forename .
                             ?iri <%s> ?surname .
                             ?iri <%s> ?age .
                             ?iri <%s> ?sex
                           }"
                           subject-ont-iri
                           forename-iri
                           surname-iri
                           age-iri
                           sex-iri)
        query-results (query-data query-str)]
     query-results))

(defn get-patient
 [id]
 (let [query-str (format "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                          PREFIX skos: <http://www.w3.org/2004/02/skos/core#>
                          SELECT * WHERE {
                            ?iri <%s> \"%s\" .
                            ?iri ?p ?o .
                            OPTIONAL { ?o skos:prefLabel ?label }
                          }"
                          subject-ont-iri id)
       query-results (query-data query-str)]
    query-results))

;;
;; Data importation/pipeline
;;

(declare trans->comp)

(def transformers
  {"int" (fn [transformer [tag field]]
           [:int (Integer. field)])
   "string" (fn [transformer [tag field]]
                [:string field])
   "decimal" (fn [transformer [tag field]]
               [:decimal (Double. field)])
   "list" (fn [transformer [tag field]]
             [:list (map #(list :string (str/trim %1)) (str/split field #"[;,]"))])
   "map" (fn [transformer [tag field]]
           [:list (map (trans->comp (get transformer "filters")) field)])
   "lookup" (fn [transformer [tag field]]
              ;(clojure.pprint/pprint {:LOOKUP field})
              (let [completions (term/search-terms (str/lower-case field))]
                (if (seq completions)
                  (do ;(clojure.pprint/pprint {:relation-found completions :tag tag :field field})
                      [:relation (:uri (first completions))])
                  (do ;(clojure.pprint/pprint {:no-relation completions :tag tag :field field})
                      [:string field]))))})

(defn trans->comp
  [transformer-list]
  (apply comp (reverse (map #(partial (get transformers (get %1 "name")) %1) transformer-list))))

; TODO: Move this to config
(def null-data-strings ["N/A", "n/a", ""])

(defn row-trans-filter
  [row]
  (fn [transform]
    (let [colname (get transform "col")
          col (get row colname)]
          (and col (not (.contains null-data-strings col))))))

(defn apply-transform
  [row transform]
  (let [col (get transform "col")
        field [:string (str/trim (get row col))]
        predicate (get transform "predicate")
        filters (get transform "filters")]
   [predicate
    ((trans->comp filters) field)]))

(defn process-row
  [row transforms id-col]
  (let [valid-trans (filter (row-trans-filter row) transforms)
        trans-row (map (partial apply-transform row) valid-trans)
        trans-row-map (into {} trans-row)]
    (assoc trans-row-map subject-ont-iri [:string (get row id-col)])))

(defn process-data
  [in-data transforms id-col]
  (doall (map (fn [row]
                (process-row row transforms id-col))
              in-data)))

(defn iri-to-parts
  [iri]
  (let [hash-parts (str/split iri #"#")
        slash-parts (str/split iri #"/")]
    (if (> (count hash-parts) 1)
      [(str (str/join "#" (butlast hash-parts)) "#")
       (last hash-parts)]
      [(str (str/join "/" (butlast slash-parts)) "/")
       (last slash-parts)])))

(defn csv-data->maps [csv-data]
  (map zipmap
       (->> (first csv-data) ;; First row is the header
                                        ;            (map keyword) ;; Drop if you want string keys instead
            repeat)
       (rest csv-data)))

(defn convert-data
  [id raw-file id-col transforms]
  (let [raw-file (.toFile (mk-path [(dataset-rawdir id) (.getName raw-file)]))
        in-data (with-open [reader (io/reader raw-file)]
                  (csv-data->maps (doall (csv/read-csv reader))))
        proc-data (process-data in-data transforms id-col)
        root-uri (dataset-root id)]
    (with-dataset DbCon ReadWrite/WRITE
      (do
        (doseq [row proc-data]
          (let [model (.getNamedModel DbCon (format "datasets/%s" id))
                resource (.createResource model (str root-uri "#" (second (get row subject-ont-iri))))
                row-data (into {} (map (fn [[iri [tag data]]]
                                         (if (= tag :list)
                                           [iri data]
                                           [iri (list [tag data])]))
                                       row))]
                                        ;            (clojure.pprint/pprint "Row data: ")
                                        ;            (clojure.pprint/pprint row-data)
            (doseq [[iri entries] row-data]
                                        ;              (clojure.pprint/pprint (str "Processing " iri " for " resource))
              (let [[name-space local-name] (iri-to-parts iri)
                    property (.createProperty model name-space local-name)]
                (doseq [[tag data] entries]
                  (cond
                    (some #(= tag %1) [:string :int :decimal])
                      (do ;(clojure.pprint/pprint (str "Adding to res " resource " iri " property " literal " data))
                        (.addLiteral resource property data))
                    (= tag :relation)
                      (do ;(clojure.pprint/pprint (str "RELATION| " resource " iri " property " relation " data))
                        (.addProperty resource property (.createResource model data)))))))))
        (println "Committed to dataset")
        {:rdf proc-data}))))

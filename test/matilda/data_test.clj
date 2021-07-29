(ns matilda.data-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [matilda.test-utils :refer [read-json-file
                                        with-test-env
                                        create-temp-file
                                        matilda-test-path
                                        matilda-test-url
                                        create-test-data-files
                                        test-data-out]]
            [matilda.queries :refer [query-data make-query]]
            [matilda.core :refer :all]
            [matilda.data :as mdata]))


(def transformer-csv-simple
  [{"col" "age"
    "predicate" mdata/age-iri
    "filters" [{"name" "int" "args" {}}]}
   {"col" "gender"
    "predicate" mdata/sex-iri
    "filters" [{"name" "string" "args" {}}]}
   {"col" "medications"
    "predicate" mdata/drug-iri
    "filters" [{"name" "list" "args" {}}
               {"name" "map" "filters" [{"name" "lookup"
                                         "args" {}}]}]}
   {"col" "conditions"
    "predicate" mdata/condition-iri
    "filters" [{"name" "list" "args" {}}
               {"name" "map" "filters" [{"name" "lookup"
                                         "args" {}}]}]}])

(def test-labels
  {"amlodipine" "amlodipine"
   "chronic kidney disease" "kidney_disease"
   "chronic obstructive lung disease" "copd"
   "copd" "copd"
   "dapagliflozin" "dapagliflozin"
   "diabetes type i" "diabetes_type_1"
   "diabetes type ii" "diabetes_type_2"
   "heart disease" "heart_disease"
   "insulin" "insulin"
   "ipratropium" "ipratropium_bromide"
   "lung cancer" "lung_cancer"
   "morphine" "morphine"
   "salbutamol" "albuterol"
   "sertraline" "sertraline"
   "terbutaline" "terbutaline"})

(defn test-lookup
  [term]
  (as-> term $r
    (str/lower-case $r)
    (get test-labels $r)
    (format "%s%s" matilda-test-url $r)))

(defn mk-test-triple
  [s p o]
  {"?s" s "?p" p "?o" o})

(defn mk-base-dataset-triples
  [d-id d-name d-desc rawfile-name]
  (let [d-iri (format "%s#%s" (mdata/dataset-root d-id) d-id)
        metainfo-triple (partial mk-test-triple d-iri)]
    (set [(metainfo-triple "http://purl.org/dc/elements/1.1/title" d-name)
          (metainfo-triple "http://purl.org/dc/elements/1.1/description" d-desc)
          (metainfo-triple (mdata/id-iri) d-id)
          (metainfo-triple (mdata/rawfile-iri) rawfile-name)
          (metainfo-triple "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
                           (mdata/dataset-iri))])))

(defn mk-patient-triples
  [dataset-id patient]
  (let [iri (format "%s#%s" (mdata/dataset-root dataset-id) (get patient "id"))
        mk-pat-triple (partial mk-test-triple iri)]
    (concat
     [(mk-pat-triple mdata/subject-ont-iri (get patient "id"))
      (mk-pat-triple mdata/sex-iri (get patient "gender"))
      (mk-pat-triple mdata/age-iri (get patient "age"))]
     (map #(mk-pat-triple mdata/condition-iri (test-lookup %))
          (get patient "conditions"))
     (map #(mk-pat-triple mdata/drug-iri (test-lookup %))
          (get patient "medications")))))

(defn mk-all-patient-triples
  [dataset-id patients]
  (set (reduce #(concat %1 (mk-patient-triples dataset-id %2)) [] patients)))

(deftest list-datasets-test
  (testing "Can list datasets"
    (is
     (with-test-env {} []
       (empty? (mdata/list-datasets))))))

(deftest create-dataset-test
  (testing "Can create a dataset"
    (is
     (with-test-env {} []
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"]
         (mdata/create-dataset id title desc)
         (= (first (mdata/list-datasets))
            {:id id :title title :description desc :files []}))))))

(deftest get-dataset-test
  (testing "Can fetch a dataset by id"
    (is
     (with-test-env {} []
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"]
         (mdata/create-dataset id title desc)
         (= (mdata/get-dataset id)
            {:id id :title title :description desc :files []}))))))

(deftest delete-dataset-test
  (testing "Can delete a dataset"
    (is
     (with-test-env {} []
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"]
         (mdata/create-dataset id title desc)
         (mdata/delete-dataset id)
         (nil? (mdata/get-dataset id)))))))

(deftest dataset-rawfiles-test
  (testing "Can fetch dataset rawfiles"
    (is
     (with-test-env {} []
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"]
         (mdata/create-dataset id title desc)
         (empty? (mdata/dataset-rawfiles id)))))))

(deftest add-raw-to-dataset-test
  (testing "Can add raw rilfes to dataset"
    (is
     (with-test-env {} []
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"
             raw-file (.toFile (create-temp-file))]
         (mdata/create-dataset id title desc)
         (mdata/add-raw-to-dataset id raw-file)
         (= (first (mdata/dataset-rawfiles id))
            (.getName raw-file)))))))

;; TODO:
;; * Stringify patient ID
(deftest convert-data-test
  (testing "Can convert & annotate simple dataset"
    (with-test-env {} [[matilda-test-path matilda-test-url]]
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"
             rawfile-name "simple.csv"
             patients (read-json-file (io/file "resources" "data" "patients.json"))
             base-triples (mk-base-dataset-triples id title desc rawfile-name)
             patient-triples (mk-all-patient-triples id patients)
             expected-triples (set/union base-triples patient-triples)
             ont-triples (set (query-data (make-query {} "?s ?p ?o")))]
         (mdata/create-dataset id title desc)
         (create-test-data-files test-data-out)
         (mdata/add-raw-to-dataset id (io/file test-data-out rawfile-name))
         (mdata/convert-data "testset" 
                             (io/file rawfile-name)
                             "id"
                             transformer-csv-simple)
         (is (= expected-triples
                (set/difference (set (query-data (make-query {} "?s ?p ?o")))
                                ont-triples)))))))

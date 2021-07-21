(ns matilda.data-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [matilda.test-utils :refer [with-test-env
                                        create-temp-file
                                        matilda-test-path
                                        matilda-test-url
                                        create-test-data-files
                                        test-data-out]]
            [matilda.core :refer :all]
            [matilda.data :as mdata]))

;; patient_id,gender,age,conditions,medications
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

(deftest convert-data-test
  (testing "Can convert & annotate simple dataset"
    (with-test-env {} [[matilda-test-path matilda-test-url]]
       (let [id "testset"
             title "Test Dataset"
             desc "test desc"]
         (mdata/create-dataset id title desc)
         (create-test-data-files test-data-out)
         (mdata/add-raw-to-dataset id (io/file test-data-out "simple.csv"))
         (is (= (first (mdata/dataset-rawfiles id))
                "simple.csv"))))))


;; TODO
;; test conversion data
;; lum data as baseline comparison


;; upload raw dataset?
;; add datasets to named graph:
;;   root graph node denotes dataset + meta w/ dc: ns assertions e.g.:
;;     {
;;       ?ont rdf:type ns:dataset .
;;       ?ont dc:title "Dataset Name" .
;;       ?ont dc:description "Dataset description" .
;;       ?ont ns:id "some-dataset-id"
;;     }
;;     where ?ont ~ "https://example.org/datasets/<id>"
;;
;; add simple dataset, test exists in list
;; add complex dataset, query properly annotated
;; list datasets
;; delete a dataset
;; get a dataset
;;
;; MATILDA ontology for internal data representation
;; $matilda/terminology#
;;   #Dataset - class

;; dron
;; ~/ontologies/dron-full.owl
;; http://purl.obolibrary.org/obo/dron.owl#
;;
;; doid
;; ~/ontologies/doid.owl
;; http://purl.obolibrary.org/obo/doid.owl#

(ns matilda.ontologies-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [matilda.test-utils :refer [with-test-env
                                        pizza-file-url
                                        pizza-ont-url]]
            [matilda.core :refer :all]
            [matilda.ontologies :as monts]))


;; Test list empty
;; Test load files
;; Test list contains onts
;; Test delete ont
;; Test list not contains ont
;; Test delete rest
;; Test list empty
;; Test load URLs
;; Test list contains onts

(deftest list-ontologies-test
  (testing "Ontology listing works"
    (is 
     (with-test-env {} []
       (empty? (monts/list-ontologies))))
    (is
     (with-test-env {} [[pizza-file-url pizza-file-url]]
       (= 1 (count (monts/list-ontologies)))))))

(deftest load-ontology-by-url-test
  (testing "Loading ontology via URL works"
    (is
     (with-test-env {} []
       (monts/load-ontology-by-url pizza-file-url)
       (let [listed (monts/list-ontologies)]
         (and (count listed)
              (= pizza-ont-url (:ont (first listed)))))))))

(deftest delete-ontology-test
  (testing "Loading ontology via URL works"
    (is
     (with-test-env {} [[pizza-file-url pizza-file-url]]
       (monts/delete-ontology pizza-file-url)
       (empty? (monts/list-ontologies))))))

(deftest load-ontology-file-test
  (testing "Loading ontology via URL works"
    (is
     (with-test-env {} [[pizza-file-url pizza-file-url]]
       (let [listed (monts/list-ontologies)]
         (and (> (count listed) 0)
              (= pizza-ont-url (:ont (first listed)))))))))


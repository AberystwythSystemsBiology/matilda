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
  (:require [mount.core :refer [defstate]]
            [matilda.config :refer [ConfMgr]]
            [omniconf.core :as cfg]
            [matilda.queries :refer [query-data]]
            [matilda.db :refer [result->map]]
            [matilda.config :refer [ConfMgr]]))


(def subject-ont-iri "http://purl.obolibrary.org/obo/NCIT_C48910")
(def forename-iri "http://purl.obolibrary.org/obo/NCIT_C40974")
(def surname-iri "http://purl.obolibrary.org/obo/NCIT_C40975")
(def condition-iri "http://purl.obolibrary.org/obo/NCIT_C2991")
(def drug-iri "http://purl.obolibrary.org/obo/NCIT_C459")
(def race-iri "http://purl.obolibrary.org/obo/NCIT_C17049")
(def age-iri "http://purl.obolibrary.org/obo/NCIT_C154631")
(def sex-iri "http://purl.obolibrary.org/obo/NCIT_C28421")

(defn list-datasets
  []
  (let [datasets (cfg/get :datasets)]
    datasets))

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

(defn get-dataset
  [id]
  (throw (UnsupportedOperationException.)))

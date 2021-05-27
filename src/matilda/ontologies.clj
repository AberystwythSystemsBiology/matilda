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
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [matilda.config :refer [ConfMgr]]
            [matilda.queries :refer [make-query query-data]]
            [matilda.db :refer [DbCon with-dataset]]))


(defn query-ontologies
  []
  (let [qstr  (make-query {}
                          "?ont rdf:type owl:Ontology ."
                          "?ont dc:title ?title ."
                          "?ont dc:description ?desc")]
    (query-data qstr)))

(defn list-ontologies
  []
  (let [ontologies (query-ontologies)]
    (map (fn [{ont "?ont" title "?title" desc "?desc"}]
           {:ont ont :title title :desc desc})
         ontologies)))

(defn delete-ontology
  [url]
  (with-dataset DbCon ReadWrite/WRITE
    (when (.containsNamedModel DbCon url)
      (.removeNamedModel DbCon url)
      true)))

(defn load-ontology-by-url
  [url]
  (with-dataset DbCon ReadWrite/WRITE
    (let [model (.getNamedModel DbCon url)]
      (.read model url)
      nil)))

(defn load-ontology-file
  [file-name url]
  (with-dataset DbCon ReadWrite/WRITE
    (let [model (.getNamedModel DbCon url)]
      (with-open [r (clojure.java.io/input-stream file-name)]
        (.read model r url)
        nil))))


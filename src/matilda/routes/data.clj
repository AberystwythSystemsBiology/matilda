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


(ns matilda.routes.data
  (:gen-class)
  (:require [matilda.data :as data]))

(defn data-list-handler
  [request]
  {:status 200
   :body (data/list-datasets)})

(defn get-dataset-handler
  [request]
  (let [dataset-id (get-in request [:params :id])]
    (data/get-dataset dataset-id)))

(defn list-patients-handler
  [request]
  (let [patients (data/list-patients)]
    {:status 200
      :body patients}))

(defn get-patient-handler
  [request]
  (let [patient (data/get-patient (get-in request [:path-params :id]))]
    {:status 200
      :body patient}))

(def data-routes
  [["/data"
    ["/" {:get {:handler data-list-handler}}]
    ["/patient"
     ["/" {:get {:handler list-patients-handler}}]
     ["/:id" {:get {:handler get-patient-handler}}]]
    ["/:id" {:get {:handler get-dataset-handler}}]]])

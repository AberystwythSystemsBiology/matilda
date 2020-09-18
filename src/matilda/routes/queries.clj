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


(ns matilda.routes.queries
  (:gen-class)
  (:require [matilda.term :refer [search-terms]]
            [matilda.queries :refer [cui->uri
                                     query-data
                                     query-data-j
                                     query-data-grouped
                                     describe-item]]
            [matilda.sparql :refer [json->sparql]]))

(defn term-dict->term-info
  [{uri :uri name :label}]
  (merge (describe-item uri)
         {:name name
          :uri uri}))

(defn term-query-handler
  [request]
  (let [terms (search-terms (get-in request [:path-params :term]))
        terms-with-info (map term-dict->term-info terms)]
    {:status 200
     :body terms-with-info}))

(defn json-query-handler
  [request]
  (let [json-query (get-in request [:body "query"])
        query-str (json->sparql json-query)
        group-key (get json-query "groupKey")]
    {:status 200
     :body {:results (query-data-grouped query-str group-key)}}))

(defn sparql-query-handler
  [request]
  (let [query-str (get-in request [:body "query"])]
    {:status 200
     :body {:results (query-data query-str)}}))

(defn sparql-query-handler-j
  [request]
  (let [query-str (get-in request [:body "query"])]
    {:status 200
     :body (query-data-j query-str)}))

(defn describe-uri-handler
  [request]
  (let [uri (get-in request [:body "uri"])]
    {:status 200
     :body (describe-item uri)}))

(defn describe-cui-handler
  [request]
  (let [cui (get-in request [:path-params :cui])
        uri (cui->uri cui)]
    (if uri
      {:status 200
       :body (describe-item uri)}
      {:status 404})))


(def query-routes
  [["/query"
    ["/term/:term" {:get {:parameters {:path {:term string?}}
                          :handler term-query-handler}}]
    ["/describe"
     ["/uri" {:post {:parameters {:body {:uri string?}}
                     :handler describe-uri-handler}}]
     ["/cui/:cui" {:get {:parameters {:path {:cui string?}}
                         :handler describe-cui-handler}}]]
    ["/json" {:post {:parameters {:body {:query map?
                                         :group-key string?}}
                     :handler json-query-handler}}]
    ["/sparql" {:post {:parameters {:body {:query string?}}
                       :handler sparql-query-handler}}]
    ["/sparqlj" {:post {:parameters {:body {:query string?}}
                        :handler sparql-query-handler-j}}]]])

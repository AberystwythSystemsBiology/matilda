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


(ns matilda.core
  (:gen-class)
  (:require [mount.core :refer [defstate]]
            [omniconf.core :as cfg]
            [clojure.tools.logging :as log]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :refer [wrap-json-body
                                          wrap-json-response]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [reitit.ring :as ring]
            [matilda.routes.ontologies :refer [ontology-routes]]
            [matilda.routes.queries :refer [query-routes]]
            [matilda.routes.data :refer [data-routes]]
            [matilda.routes.annotate :refer [annotate-routes]]
            [matilda.term :refer [TermSearcher]]
            [matilda.config :refer [ConfMgr]]))


(def debug-routes
  [["/debug"
    ["/mount" {:get {:handler (fn [req] (mount.core/start #'matilda.db/DbCon))}}]
    ["/unmount" {:get {:handler (fn [req] (mount.core/stop #'matilda.db/DbCon))}}]]])

(def request-handler
  (ring/ring-handler
    (ring/router
     [ontology-routes
      query-routes
      data-routes
      annotate-routes
      debug-routes])
    (ring/create-default-handler)))

(def app
  (-> request-handler
      wrap-params
      wrap-multipart-params
      wrap-json-body
      wrap-json-response))

(def WebDaemon (atom {}))

(defn start-web
  []
  (let [jetty-port (cfg/get :jetty-port)]
    (swap! WebDaemon assoc :server (jetty/run-jetty #'app {:port jetty-port :join? false}))))

(defn stop-web
  []
  (.stop (:server WebDaemon)))
(defstate WebDaemon :start (start-web)
                    :stop (stop-web))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (log/info "Initialized in production mode")
  (mount.core/start)
  (clojure.pprint/pprint WebDaemon))

(defn dev-init
  []
  (log/info "Initialized in debug mode")
  (mount.core/start-without #'matilda.core/WebDaemon))

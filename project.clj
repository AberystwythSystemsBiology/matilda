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


(defproject matilda "0.1.0-SNAPSHOT"
  :description "MATILDA server software, for managing clinical data using ontologies"
  :url "https://github.com/AberystwythSystemsBiology/matilda"
  :license {:name "GPLv3 with additions"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler matilda.core/app
         :init matilda.core/dev-init}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.namespace "1.0.0"]
                 [org.clojure/tools.trace "0.7.9"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.apache.jena/apache-jena-libs "3.10.0" :extension "pom"]
                 [clj-http "3.10.1"]
                 [ring/ring-core "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-jetty-adapter "1.6.3"]
                 [javax.servlet/servlet-api "2.5"]
                 [mount "0.1.16"]
                 [metosin/reitit "0.3.10"]
                 [org.slf4j/slf4j-api "1.7.28"]
                 [org.lundez/symspell "1.0-SNAPSHOT"]
                 [org.apache.logging.log4j/log4j-core "2.12.1"]
                 [org.apache.logging.log4j/log4j-api "2.12.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.12.1"]
                 [com.grammarly/omniconf "0.4.3"]
                 [org.xerial/sqlite-jdbc "3.32.3.2"]]
  :dev-dependencies [[cheshire "5.10.0"]]
  :jvm-opts ["-Xmx20g" "-Xms20g"]
  :main ^:skip-aot matilda.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

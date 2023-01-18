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


(ns matilda.jsparql
  (:gen-class)
  (:require [clojure.string :as str]))

(defn term-to-str
  [term bindings]
  (case (:type term)
    "variable" (format "?%s" (:value term))
    "curie" (format "%s:%s" (:namespace term) (:value term))
    "iri" (format "<%s>" (:value term))
    "binding" (term-to-str (get bindings (keyword (:value term))) bindings)
    "string" (format "\"%s\"" (:value term))
    "number" (str (:value term))
    "raw" (str (:value term))))

(defn triple-to-str
  [m bindings]
  (let [subject (term-to-str (:subject m) bindings)
        predicate (term-to-str (:predicate m) bindings)
        object (term-to-str (:object m) bindings)]
    (format "%s %s %s" subject predicate object)))

(declare stmt-to-str)

(defn and-to-str
  [m bindings]
  (->> (:conditions m)
       (map #(stmt-to-str % bindings))
       (str/join " . ")))

(defn or-to-str
  [m bindings]
  (->> (:conditions m)
       (map (comp #(format " { %s } " %) #(stmt-to-str % bindings)))
       (str/join "\nUNION\n")))

(defn stmt-to-str
  [m bindings]
  (case (:type m)
    "triple" (triple-to-str m bindings)
    "and" (and-to-str m bindings)
    "or" (or-to-str m bindings)))

(defn namespaces-to-str
  [namespaces]
  (->> namespaces
       (map (fn [[prefix iri]] 
              (format "PREFIX %s: <%s>" (name prefix) iri)))
       (str/join "\n")))

(defn json->sparql
  [json-query]
  (let [{:keys [namespaces bindings query]} json-query
        prefixes (namespaces-to-str namespaces)
        query-body (stmt-to-str query bindings)]
    (format "%s\nSELECT * WHERE {\n%s\n}" prefixes query-body)))
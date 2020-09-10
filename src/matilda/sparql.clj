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

(ns matilda.sparql
  (:gen-class)
  (:require [clojure.string :refer [join]]))

(defn j-val->s-val
  "Convert JSON value definition to SPARQL representation"
  [val]
  (let [value (get val "value")]
    (case (get val "type")
      "iri" (format "<%s>" value)
      "iriShort" (format "%s" value)
      "number" value
      "string" (format "\"%s\"" value))))

(defn j-vars->vars
  "Convert JSON value definitions to SPARQL representations"
  [vals]
  (into {} (map (fn [[k v]] [k (j-val->s-val v)]) vals)))

(defn j->s-atom
  "Convert JSON value atom to SPARQL representation (e.g. binding, iri, etc.)"
  [vars atom]
  (let [val (get atom "value")
        val-type (get atom "type")]
    (cond
      (= val-type "binding") (format "?%s" val)
      (= val-type "variable") (get vars val);(get values val)
      :else (j-val->s-val atom))))

(defn j->s-assert
  "Convert simple JSON triple expression to SPARQL representation"
  [vars assertion]
  (let [subj (j->s-atom vars (get assertion "subj"))
        pred (j->s-atom vars (get assertion "pred"))
        obj (j->s-atom vars (get assertion "obj"))]
    (format "%s %s %s" subj pred obj)))

;; Declare ahead of time due to the necessity of mutual recursion
(declare j-clauses->s-clauses)

(defn j->s-union
  "Convert JSON union expression to SPARQL representation"
  [values uniondef]
  (let [groups (get uniondef "clauses")
        groups-s (map #(j-clauses->s-clauses values %1) groups)]
    (join " UNION " (map #(format "{ %s }" %1) groups-s))))

(def filter-types
  "Lookup table for filter type -> SPARQL filter expression string"
  {"exists" "EXISTS"
   "notExists" "NOT EXISTS"})

(defn j->s-filter
  "Convert JSON filter expression to SPARQL representation"
  [vars filterdef]
  (let [filter-type (get filter-types (get filterdef "filterType"))
        clauses (j-clauses->s-clauses vars (get filterdef "clauses"))]
    (format "FILTER %s { %s }" filter-type clauses)))

(defn j-clause->s-clause
  "Convert a JSON clause to SPARQL representation.
   A clause is the most top-level generic idea of a query part.
   This ranges from simple <subj> <pred> <obj> triples to more complex
   statements such as UNION, FILTER, etc."
  [vars clause]
  (case (get clause "type")
    "assertion" (j->s-assert vars clause)
    "union" (j->s-union vars clause)
    "filter" (j->s-filter vars clause)))

(defn j-clauses->s-clauses
  "Convert JSON clauses to a SPARQL representation, interposing with periods"
  [vars clauses]
  (join " . " (map (fn [clause] (j-clause->s-clause vars clause))
                   clauses)))

(defn j->s-ns
  "Convert JSON namespace PREFIX definition to SPARQL representation."
  [namespaces]
  (join "\n" (map (fn [[ns iri]]
                    (format "PREFIX %s: <%s>" ns iri))
                  namespaces)))

(defn json->sparql
  "Convert a JSON query definition to a SPARQL query string"
  [json]
  (let [namespaces (j->s-ns (get json "namespaces"))
        vars (j-vars->vars (get json "variables"))
        clauses (j-clauses->s-clauses vars (get json "clauses"))]
    (format "%s\nSELECT * WHERE { %s }"
            namespaces
            clauses)))
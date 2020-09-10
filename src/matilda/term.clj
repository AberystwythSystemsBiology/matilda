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


(ns matilda.term
  (:gen-class)
  (:import [java.io FileNotFoundException]
           [SymSpell SymSpell])
  (:require [cheshire.core :refer [generate-stream parse-stream]]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [clojure.java.io :as io]
            [omniconf.core :as cfg]
            [matilda.config :refer [ConfMgr]]
            [matilda.queries :refer [query-data make-query]]))


(def root-drug "http://purl.bioontology.org/ontology/SNOMEDCT/105590001")
(def root-condition "http://purl.bioontology.org/ontology/SNOMEDCT/404684003")
(def root-finding "http://purl.bioontology.org/ontology/SNOMEDCT/has_finding_site")

(defn make-dict-from-terms
  [terms]
  (let [symspell (SymSpell. -1 2 -1 1)]
    (doall
     (->> terms
          (map (fn [[term labels]]
                 (->> labels
                      (map (fn [label]
                             (.createDictionaryEntry symspell
                                                     label
                                                     1
                                                     nil)
                             {label term}))
                      (reduce merge))))
          (reduce merge)
          (vector symspell)))))

(defn get-term-labels
  []
  (let [query-str (make-query {}
                              "{ ?iri rdfs:label ?label } "
                              "UNION"
                              "{ ?iri skos:prefLabel ?label }"
                              "UNION"
                              "{ ?iri skos:altLabel ?label }")
        result (query-data query-str)]
    (reduce (fn [terms {iri "?iri" label "?label"}]
              (merge-with into terms {iri [(.toLowerCase label)]}))
            {}
            result)))

(defn load-or-make-dict
  [infile]
  (try
    (with-open [infile-reader (io/reader infile)]
      (log/info "Term file find, loading...")
      (let [json (parse-stream infile-reader)
            [symspell term-dict] (make-dict-from-terms json)]
        (log/info "Created symspell/term-dict")
        [symspell term-dict]))
    (catch FileNotFoundException _
      (log/info "No term file find, populating...")
      (let [terms (get-term-labels)
            [symspell term-dict] (make-dict-from-terms terms)]
        (log/info "Created symspell/term-dict")
        (generate-stream terms
                         (io/writer infile))
        (log/info "Stored term file to disk")
        [symspell term-dict]))))

(def TermSearcher)

(defn term-file
  [file-name]
  (format "%s/%s.json" (cfg/get :term-dir) file-name))

; (defn init-terms
;   []
;   (let [[drug-symspell drug-terms] 
;           (load-or-make-dict (term-file "drugs") root-drug)
;         [finding-symspell finding-terms] 
;           (load-or-make-dict (term-file "findings") root-finding)
;         [condition-symspell condition-terms]
;           (load-or-make-dict (term-file "conditions") root-condition)]
;    {:drug-terms drug-terms :drug-symspell drug-symspell
;     :finding-terms finding-terms :finding-symspell finding-symspell
;     :condition-terms condition-terms :condition-symspell condition-symspell}))

(defn init-terms
  []
  (let [[symspell terms] (load-or-make-dict (term-file "terms"))]
    {:symspell symspell :terms terms}))

(defn deinit-terms
  []
  nil)

(defstate TermSearcher :start (init-terms)
                       :stop (deinit-terms))

(defn search-terms
  [term]
  (let [res (.lookup (:symspell TermSearcher) term SymSpell.SymSpell$Verbosity/All)
        terms (map #(.term %1) res)]
    (map #(list %1 (get (:terms TermSearcher) %1)) terms)))

; (defn search-drug
;   [term]
;   (let [res (.lookup (:drug-symspell TermSearcher) term SymSpell.SymSpell$Verbosity/All)
;         terms (map #(.term %1) res)]
;     (map #(list %1 (get (:drug-terms TermSearcher) %1)) terms)))

; (defn search-condition
;   [term]
;   (let [res (.lookup (:condition-symspell TermSearcher) term SymSpell.SymSpell$Verbosity/All)
;         terms (map #(.term %1) res)]
;     (map #(list %1 (get (:condition-terms TermSearcher) %1)) terms)))

; (defn search-site
;   [term]
;   (let [res (.lookup (:finding-symspell TermSearcher) term SymSpell.SymSpell$Verbosity/All)
;         terms (map #(.term %1) res)]
;     (map #(list %1 (get (:finding-terms TermSearcher) %1)) terms)))

            

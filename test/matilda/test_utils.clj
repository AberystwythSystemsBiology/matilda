(ns matilda.test-utils
  (:import [java.nio.file Files FileSystems]
           [java.nio.file.attribute FileAttribute])
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer :all]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [mount.core :as mount]
            [omniconf.core :as cfg]
            [matilda.core :refer :all]
            [matilda.data :as mdata]
            [matilda.db :as mdb]
            [matilda.ontologies :as mont]
            [matilda.util :refer :all]
            [matilda.term :as mterm]
            [clojure.string :as str]))

(def pizza-file-url "https://raw.githubusercontent.com/owlcs/pizza-ontology/master/pizza.owl")
(def pizza-ont-url "http://www.co-ode.org/ontologies/pizza")
(def pizza-base-url "http://www.co-ode.org/ontologies/pizza/pizza.owl#")

(def doid-file-path "resources/ontologies/doid.owl")
(def doid-ont-url "http://purl.obolibrary.org/obo/doid.owl")
(def doid-base-url "http://purl.obolibrary.org/obo/doid.owl#")

(def dron-file-path "resources/ontologies/dron-full.owl")
(def dron-ont-url "http://purl.obolibrary.org/obo/dron.owl")
(def dron-base-url "http://purl.obolibrary.org/obo/dron.owl#")

(def matilda-test-path "resources/ontologies/matilda-test.owl")
(def matilda-test-url "http://github.com/rob-a-bolton/matilda/resources/ontologies/matilda-test#")

(def test-data-json "resources/data/patients.json")
(def test-data-out "resources/data/out")

(defn file-contained-by?
  [root file]
  (.startsWith (.toPath (io/file file))
               (.toPath (io/file root))))

(defn files-contained-by?
  [root paths]
  (every? #(file-contained-by? root %1) paths))

(defn mk-tmp [] (Files/createTempDirectory "matilda-test-" (make-array FileAttribute 0)))

(defn create-test-config
  [tmp-dir cfg]
  (merge cfg
         {:tmp-dir tmp-dir
          :jetty-port 3093
          :matilda-ont-root "https://matilda.localhost/terminology/matilda#"
          :matilda-dataset-root "https://matilda.localhost/datasets"
          :jdbc {:dbtype "sqlite"
                 :dbname (->> (mk-path ["db.sqlite3"])
                              (.resolve tmp-dir)
                              .toFile
                              .getAbsolutePath)}
          :data-dir (->> (mk-path ["data"])
                         (.resolve tmp-dir)
                         .toFile
                         .getAbsolutePath)
          :tdb-dir (->> (mk-path ["tdb"])
                        (.resolve tmp-dir)
                        .toFile
                        .getAbsolutePath)
          :term-dir (->> (mk-path ["terms"])
                         (.resolve tmp-dir)
                         .toFile
                         .getAbsolutePath)}))

(defn setup-test-env
  [conf ontologies]
  (let [test-conf (create-test-config (mk-tmp) conf)
        {:keys [tdb-dir term-dir data-dir]} test-conf]
    (.mkdir (io/file data-dir))
    (.mkdir (io/file tdb-dir))
    (.mkdir (io/file term-dir))
    (cfg/populate-from-map test-conf)
    (-> (mount/except [#'matilda.core/WebDaemon #'matilda.term/TermSearcher])
        (mount/swap {#'matilda.config/ConfMgr test-conf})
        (mount/start))
    (when (seq ontologies)
      (doseq [[fname url] ontologies] (mont/load-ontology-file fname url)))
    (mount/start #'matilda.term/TermSearcher)
    test-conf))

(defn destroy-test-env
  [conf]
  (let [tmp-dir (:tmp-dir conf)]
    (mount/stop)
    (when (.isAbsolute tmp-dir)
      (delete-dir! (.toFile tmp-dir)))))

(defmacro with-test-env
  [conf ontologies & body]
  `(let [test-env# (setup-test-env ~conf ~ontologies)
         res# (do ~@body)]
     (destroy-test-env test-env#)
     res#))

(defn create-temp-file
  []
  (Files/createTempFile "clj-" nil (make-array FileAttribute 0)))

(defn read-json-file
  [file-name]
  (with-open [is (io/reader file-name)]
    (cheshire/decode-stream is)))

(defn idmap
  "Takes an ID key, a val key, and collection and returns vecs of [id val]
  mapped against the val key of each item in the collection
  e.g. {:id 1 :stuff [:a :b]}
  and produces [[1 :a] [1 :b]]"
  [k-id k-val coll]
  (mapcat (fn [obj] (map (fn [val] [(get obj k-id) val]) (get obj k-val))) coll))

(defn create-test-data-csv-simple
  [patients out-dir]
  (with-open [os (io/writer (io/file out-dir "simple.csv"))]
    (csv/write-csv
     os
     (cons ["id" "gender" "age" "conditions" "medications"]
           (map (fn [row]
                  [(get row "id")
                   (get row "gender")
                   (get row "age")
                   (str/join ";" (get row "conditions"))
                   (str/join ";" (get row "medications"))])
                patients)))))

(defn create-test-data-csv-complex
  [patients out-dir]
  (with-open [os (io/writer (io/file out-dir "complex1.csv"))]
    (csv/write-csv
     os
     (cons ["id" "gender" "age"]
           (map (fn [patient]
                  [(get patient "id")
                   (get patient "gender")
                   (get patient "age")])
                patients))))
  (with-open [os (io/writer (io/file out-dir "complex2.csv"))]
    (csv/write-csv
     os
     (cons ["id" "condition"]
           (idmap "id" "conditions" patients))))
  (with-open [os (io/writer (io/file out-dir "complex3.csv"))]
    (csv/write-csv
     os
     (cons ["id" "medication"]
           (idmap "id" "medications" patients)))))

(defn create-test-data-json-simple
  [patients out-dir]
  (with-open [os (io/writer (io/file out-dir "simple.json"))]
    (cheshire/encode-stream patients os)))

(defn create-test-data-json-complex
  [patients out-dir]
  (with-open [os (io/writer (io/file out-dir "complex1.json"))]
    (cheshire/encode-stream
     (map #(select-keys % ["id" "age" "gender"]) patients)
     os))
  (with-open [os (io/writer (io/file out-dir "complex-a1.json"))]
    (cheshire/encode-stream
     (map #(select-keys % ["id" "conditions"]) patients)
     os))
  (with-open [os (io/writer (io/file out-dir "complex-a2.json"))]
    (cheshire/encode-stream
     (map #(select-keys % ["id" "medications"]) patients)
     os))
  (with-open [os (io/writer (io/file out-dir "complex-b.json"))]
    (cheshire/encode-stream
     (map #(select-keys % ["id" "medications"]) patients)
     os)))

(defn create-test-data-files
  [dir]
  (let [patients (read-json-file test-data-json)]
    (create-test-data-csv-simple patients dir)))

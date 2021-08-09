(ns matilda.util
  (:import [java.nio.file Files FileSystems])
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [omniconf.core :as cfg]))

(defn mk-path
  [path-parts]
  (let [parts (map str path-parts)
        fst (first parts)
        rest (into-array String (drop 1 parts))]
    (.getPath (FileSystems/getDefault)
              fst
              rest)))

(defn file-type
  [f]
  (if (.isDirectory f)
    :dir
    :file))

(defn build-file-list
  [dir-name]
  (loop [res [(io/file dir-name)]
         paths (.listFiles (io/file dir-name))]
    (let [{files :file dirs :dir} (group-by file-type paths)]
      (if (empty? paths)
        res
        (recur (concat res files dirs)
               (apply concat (map #(.listFiles %1) dirs)))))))

(defn delete-dir!
  [dir-name]
  (doseq [f (reverse (build-file-list dir-name))]
    (.delete f)))

(defn mk-matilda-term
  [term]
  (format "%s%s" (cfg/get :matilda-ont-root) term))
(ns mbs-se-pv.views.util
  (:use [org.clojars.smee.util :only (per-thread-singleton)]
        [clojure.java.io :only (resource reader)]
        [clojure.string :only (join)]))

(def timeformat (per-thread-singleton #(java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")))
(def dateformat (per-thread-singleton #(java.text.SimpleDateFormat. "dd.MM.yyyy")))
(def dateformatrev (per-thread-singleton #(java.text.SimpleDateFormat. "yyyyMMdd")))

(def ^:const ONE-DAY (* 24 60 60 1000))

(defn escape-dots [^String s]
  (.replace s \. \'))

(defn de-escape-dots [^String s]
  (.replace s \' \.))

(defn extract-wr-id [s] 
  (second (re-find #".*\.wr\.(\d+).*" s)))

;;;;;;;;;;;;;;;;;;;;;;;;; recreate hierarchy from hierarchy segments ;;;;;;;;;;;;;;;;;;;;;

(defn add-path [h path] 
  (let [dir (butlast path) 
        entry (last path)] 
    (update-in h dir (fn [x] (if x (conj x entry) [entry]))))) 

(defn restore-hierarchy [paths] 
  (reduce add-path {} paths))


(defn render-javascript-template
  [resource-path & params]
  (apply format (->> resource-path resource reader line-seq (join "\n")) params))


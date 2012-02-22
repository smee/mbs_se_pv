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

(defn convert-si-unit 
  "Convert a number in the most fitting SI unit. Returns a vector of scaled number and unit prefix.
 (convert-si-unit 0.02)
 => [20.0 \\m]"
  [n]
  (some (fn [[mag suffix]] (when (>= n mag) [(/ n mag) suffix])) 
        [[1e12 \T] 
         [1e9  \G] 
         [1e6  \M] 
         [1e3  \k] 
         [1 nil] 
         [1e-3 \m] 
         [1e-6 \µ] 
         [1e-9 \n]]))

(defn create-si-prefix-formatter 
  "Creates an instance of java.text.NumberFormat that prints formatted doubles with their respective
SI unit prefixes"
  ([format-string] (create-si-prefix-formatter format-string ""))
  ([format-string suffix]
    (let [nf (java.text.DecimalFormat. format-string)] 
      (proxy [java.text.NumberFormat] []
        (format [n sb fp]
                (if-let [[n prefix] (convert-si-unit n) ] 
                  (.append sb (str (.format nf n) prefix suffix))
                  (do  (.format nf n sb fp) (.append sb suffix))))))))
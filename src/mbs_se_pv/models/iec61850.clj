(ns ^{:doc "excerpts from IEC61850, names for datatypes, unit kinds etc."} 
     mbs-se-pv.models.iec61850
  (:require [mbs-se-pv.models.iec61850.values :as v]))

(def abbreviations (merge v/abbrev-7-4 v/abbrev-7-420 v/abbrev-7-410))

(defn find-possible-abbrev [s]
  (->> abbreviations
    keys
    (map #(.trim %)) 
    (sort-by count)
    reverse
    (keep #(let [idx (.indexOf s %)] (when (not= -1 idx) [idx %])))
    (sort-by first)
    (partition-by first) ;; multiple matches per idx possible if one abbreviation is a prefix of another
    (map (partial apply max-key (comp count second))) ;; keep longest abbreviation per index
    reverse
    ))

(defn replace-abbrev [s [idx k]] 
  (str 
    (subs s 0 idx) 
    \space 
    (get abbreviations k) 
    \space 
    (subs s (+ idx (count k)))))

(defn decrypt-data-name 
  "Decode cryptic IEC 61850 abbreviations of data type/attribute names"
  [s]
  (let [is (find-possible-abbrev s)]    
    (.trim (reduce replace-abbrev s is))))
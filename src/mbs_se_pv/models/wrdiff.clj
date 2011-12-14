(ns mbs-se-pv.models.wrdiff
  (:require mbs-se-pv.views.util
            [mbs-db.core :as db])
  (:use [incanter core charts stats]))

#_(let [s (java.sql.Timestamp. (.getTime (.parse (mbs-se-pv.views.util/dateformatrev) "20000101")))
      e (java.sql.Timestamp. (.getTime (.parse (mbs-se-pv.views.util/dateformatrev) "20111231")))
      pac0 (db/all-values-in-time-range "abwohn.wr.0.pac" s e)
      pac1 (db/all-values-in-time-range "abwohn.wr.1.pac" s e)
      pac2 (db/all-values-in-time-range "abwohn.wr.2.pac" s e)
      metadata (db/get-metadata "abwohn")]
  (def pac0 (map #(update-in %  [:value] / (get-in metadata [:wr 0 :max-p])) pac0))
  (def pac1 (map #(update-in %  [:value] / (get-in metadata [:wr 1 :max-p])) pac1))  
  (def pac2 (map #(update-in %  [:value] / (get-in metadata [:wr 2 :max-p])) pac2)))

;; view differences between values 
(view (time-series-plot (map :time pac0) 
                        (map - (map :value pac0) (map :value pac1))))

;; there seems to be a systematic increase per day?      
(def pac0days (partition-by #(.getDate (java.util.Date. (:time %))) pac0))
(def pac1days (partition-by #(.getDate (java.util.Date. (:time %))) pac1))
(def pac2days (partition-by #(.getDate (java.util.Date. (:time %))) pac2))

(defn- times-of [day]
  (map (comp #(mod % (* 24 60 60 1000)) :time) day))
(defn- day-of-year [t]
  (let [cal (doto (java.util.Calendar/getInstance) (.setTimeInMillis t))]
    (.get cal java.util.Calendar/DAY_OF_YEAR)))
(defn- robust-lm [y x]
  (try (linear-model y x)
    (catch ArithmeticException e {:fitted (repeat (count y) 0)})))
;; plot the linear models for the differences between pac of two wr per day
;;
(let [chart (time-series-plot [] [])
      times (map times-of pac1days)
      daily-diffs (map (fn [d1 d2] (map - (map :value d1) (map :value d2))) pac0days pac2days)
      linear-models (map robust-lm daily-diffs times)]
  ;(doseq [[t lm] (map list times linear-models)]
  ;  (add-lines chart t (:fitted lm)))
  ;(view chart)
  (view (scatter-plot 
          (map (comp day-of-year :time) pac0days) 
          (map (fn [[x y]] (- x y)) (map :fitted linear-models))))
  #_(map #(->> % :fitted (take 2) (apply -) println) linear-models)
  #_(view (box-plot (map #(->> % :fitted (take 2) (apply -)) linear-models))))
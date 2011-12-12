(ns mbs-se-pv.models.wrdiff
  (:require mbs-se-pv.views.util
            [mbs-se-pv.models.db :as db])
  (:use [incanter core charts stats]))

(let [s (java.sql.Timestamp. (.getTime (.parse (mbs-se-pv.views.util/dateformatrev) "20000101")))
      e (java.sql.Timestamp. (.getTime (.parse (mbs-se-pv.views.util/dateformatrev) "20111231")))]
  (def pac0 (db/all-values-in-time-range "abwohn.wr.0.pac" s e))
  (def pac1 (db/all-values-in-time-range "abwohn.wr.1.pac" s e))
  (def pac2 (db/all-values-in-time-range "abwohn.wr.2.pac" s e))
  )

;; view differences between values 
(view (time-series-plot (map :time pac0) (map - (map :value pac0) (map :value pac2))))

;; there seems to be a systematic increase per day?      
(def pac0days (partition-by #(.getDate (java.util.Date. (:time %))) pac0))
(def pac1days (partition-by #(.getDate (java.util.Date. (:time %))) pac1))
(def pac2days (partition-by #(.getDate (java.util.Date. (:time %))) pac2))

(defn- times-of [day]
  (map (comp #(mod % (* 24 60 60 1000)) :time) day))

;; plot the linear models for the differences between pac of two wr per day
;;
(let [chart (time-series-plot [] [])
      times (map times-of pac1days)
      daily-diffs (map (fn [d1 d2] (map - (map :value d1) (map :value d2))) pac1days pac2days)
      linear-models (map linear-model daily-diffs times)]
  (doseq [[t lm] (map list times linear-models)]
    (add-lines chart t (:fitted lm)))
  (view chart)
  #_(map #(->> % :fitted (take 2) (apply -) println) linear-models)
  #_(view (box-plot (map #(->> % :fitted (take 2) (apply -)) linear-models))))
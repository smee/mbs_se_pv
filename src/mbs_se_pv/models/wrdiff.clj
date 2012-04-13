(ns mbs-se-pv.models.wrdiff
  (:require mbs-se-pv.views.util
            [mbs-db.core :as db])
  (:use [incanter core charts stats]))



(defn- times-of [day]
  (map (comp #(mod % (* 24 60 60 1000)) :time) day))
(defn- day-of-year [t]
  (let [cal (doto (java.util.Calendar/getInstance) (.setTimeInMillis t))]
    (.get cal java.util.Calendar/DAY_OF_YEAR)))
(defn- robust-lm [y x]
  (try (linear-model y x)
    (catch ArithmeticException e {:fitted (repeat (count y) 0)})))
(comment
  (def pac0 (db/all-values-of "1555.wr.0.pac"))
  (def pac1 (db/all-values-of "1555.wr.1.pac"))
  (def pac2 (db/all-values-of "1555.wr.2.pac"))
  
  
  ;; view differences between values 
  (view (time-series-plot (map :time pac0) 
                        (map - (map :value pac0) (map :value pac1))))
  
  ;; there seems to be a systematic increase per day?      
  (def pac0days (partition-by #(.getDate (java.util.Date. (:time %))) pac0))
  (def pac1days (partition-by #(.getDate (java.util.Date. (:time %))) pac1))
  (def pac2days (partition-by #(.getDate (java.util.Date. (:time %))) pac2))
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
            (map (comp day-of-year :time first) pac0days) 
            (map (fn [[x y]] (- x y)) (map :fitted linear-models))))
    #_(map #(->> % :fitted (take 2) (apply -) println) linear-models)
    #_(view (box-plot (map #(->> % :fitted (take 2) (apply -)) linear-models)))))

;; plot divergence from mean of all other timeseries (assumption: all series should have same developing, e.g. different PAC curves)
(let [t (map :time pac0)
      m0 (apply max p0)
      m1 (apply max p1)
      m2 (apply max p2)
      p0 (map #(/ % m0) p0)
      p1 (map #(/ % m1) p1)
      p2 (map #(/ % m2) p2)] 
  (doto (time-series-plot t (map #(- %1 %2) p0 (map (comp mean vector) p1 p2)))
    (add-lines t (map #(- %1 %2) p1 (map (comp mean vector) p0 p2)))
    (add-lines t (map #(- %1 %2) p2 (map (comp mean vector) p0 p1)))
    view))
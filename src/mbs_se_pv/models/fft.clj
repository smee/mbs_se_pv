(ns mbs-se-pv.models.fft
  (:require 
    [incanter.core :as ic]
    [incanter.charts :as ch]
    [mbs-se-pv.views.util :as util]
    [mbs-se-pv.models.db :as db]
    [chart.jfreechart :as cfj]))

(defn make-cyclic 
  "Convert samples into a continuous wave assuming it represents only the first half of a sinusoidal wave."
  [data]
  (cycle (concat data (map - data))))

(defn plot-pseudo-wave [data n]
  (ic/view (ch/xy-plot (range n) (take n (make-cyclic data)))))

(defn fft 
  ([data] (fft data (bit-shift-left 1 10))) ;; 2^10
  ([data n]
    (let [fft (edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D. n)
          res (double-array (* 2 n) (take n (make-cyclic (map double data))))]
      (do 
        (.complexForward fft res)
        (->> res
          (into [])
          (map #(/ % n)) ;;scale
          (partition 2)
          (map (fn [[re im]] (Math/sqrt (+ (* re re) (* im im)))))
          (take (/ n 2)))))))
  


(defn interactive [data]
  (let [chart (doto (ch/xy-plot (range 16) (repeat 0)) ic/view)]
    (cfj/sliders [n (range 2 21)]
             (let [n (bit-shift-left 1 n)
                   x (range (/ n 2))
                   y (fft data n)]   
               (println (count x) (count y))
               (ic/set-data chart [x y])))) )

(defn- get-day-of-year [millis]
  (.get 
    (doto (java.util.Calendar/getInstance) (.setTimeInMillis millis))
    java.util.Calendar/DAY_OF_YEAR))


(defn waterfall [name start-millis end-millis]
  {:pre [(>= 365 (int (/ (- end-millis start-millis) (* 24 60 60 1000))))]}
    ;; waterfall display of ffts per day
  (let [ ;; load data per day
        s (java.sql.Timestamp. start-millis) 
        e (java.sql.Timestamp. end-millis)          
        db-values (db/all-values-in-time-range name s e)
        days-count (int (/ (- end-millis start-millis) (* 24 60 60 1000)))
        avail-days (group-by (comp get-day-of-year :time) db-values)
        missing-days (into {} (for [day (range days-count) :when (not (avail-days day))]
                            [day [{:time 0 :value 0}]]))
        data-per-day (->> missing-days
                       (merge avail-days)
                       (into (sorted-map))
                       vals
                       (map (partial map :value)))       
        
        ;_ (println (count avail-days) (map (comp count second) avail-days))
        ;_ (println (count missing-days) (map (comp count second) missing-days))
        ;_ (println (count data-per-day) (map count data-per-day))
        n (bit-shift-left 1 10)
        ffts (vec (map #(vec (fft % n)) data-per-day))
        f (fn [x y] (get-in ffts [(int x) (int y)]))
        x-max (count data-per-day)
        y-max (count (first ffts))
        ]
    
    (doto (ch/heat-map f 0 x-max 0 y-max 
                       :color? true
                       :title (format "FFT (n=%d) of PAC for %s" n name)
                       :x-label "Days of year"
                       )
      (.. getPlot getRenderer (setBlockWidth 5.0))
      (.. getPlot getRenderer (setBlockHeight 5.0))
      ic/view)))

(comment
  (def data 
    (let [name "abel.wr.1.pac"
          s (->> "20110101" (.parse (util/dateformatrev)) .getTime java.sql.Timestamp.) 
          e (->> "20111231" (.parse (util/dateformatrev)) .getTime java.sql.Timestamp.)
          db-data (db/all-values-in-time-range name s e)]
      (map :value db-data)))
  
  (ic/view (range (count data)) data)
  (plot-pseudo-wave data (bit-shift-left 1 12))
  (let [n (bit-shift-left 1 12)] (ic/view (ch/xy-plot (range n) (fft (make-cyclic data) n))))
  
  (waterfall "zwonar.wr.1.temp" 
             (->> "20100101" (.parse (util/dateformatrev)) .getTime)
             (->> "20101231" (.parse (util/dateformatrev)) .getTime))

  
  )
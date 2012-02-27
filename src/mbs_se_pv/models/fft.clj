(ns mbs-se-pv.models.fft
  (:require 
    [incanter.core :as ic]
    [incanter.charts :as ch]
    [mbs-se-pv.views.util :as util]
    [mbs-db.core :as db]
    [chart-utils.jfreechart :as cfj])
  (:use [org.clojars.smee.time :only (millis-to-string as-sql-timestamp)]))

(def ^:const ONE-DAY (* 24 60 60 1000))

(defn make-cyclic 
  "Convert samples into a continuous wave assuming it represents only the first half of a sinusoidal wave.
 There are multiple ways to do it. Literature says to pad the data with zeros till the length is a power of two.
TODO: For photovoltatic data we have a natural cycle of 24 hours, so we should actually add zeros for missing values."
  [data]
  ;; make cyclic by concating mirror data (lower half of sinus wave) and cycle the result
  (cycle (concat data (map - data)))
  ;; make cyclic by just concatenating the date infinitely
  ;(cycle data)
    ;; pad with zeroes, gives interesting results, not sure about meaning, though
  ;(concat data (repeat 0))
  )

(defn fft 
  "Calculate the fast fourier transformation of the input data. Uses 'make-cyclic' on the date before running
the calculation. N is the length of the data to use as input, should be a power of two."
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

(defn plot-pseudo-wave [data n]
  (ic/view (ch/xy-plot (range n) (take n (make-cyclic data)))))

(defn- get-day-of-year [millis]
  (.get 
    (doto (java.util.Calendar/getInstance) (.setTimeInMillis millis))
    java.util.Calendar/DAY_OF_YEAR))

(defn- get-days-since [start now]
  (int (/ (- now start) ONE-DAY)))

(defn- set-frequency-axis [chart sample-len fft-len max-value]
  (let [sample-freq (/ 1 sample-len)
        freq-bin-width (/ sample-freq fft-len)
        lens (map #(/ 1 (* freq-bin-width %)) (range 1 (inc fft-len)))
        label (.. chart getPlot getRangeAxis getLabel)
        axis (doto (org.jfree.chart.axis.SymbolAxis. label (into-array (map millis-to-string lens)))
               (.setUpperBound max-value))]
        ;(println fft-len "bins à" (double freq-bin-width) "Hz")
        (.. chart getPlot (setRangeAxis axis))))


(defn waterfall [name start-millis end-millis]
 ; {:pre [(>= 365 (int (/ (- end-millis start-millis) ONE-DAY)))]}
    ;; waterfall display of ffts per day
  (let [ ;; load data per day
        s (as-sql-timestamp start-millis) 
        e (as-sql-timestamp end-millis)     
        total-days (int (/ (- end-millis start-millis) ONE-DAY))
        
        db-values (db/all-values-in-time-range name s e)
        avail-days (group-by (comp (partial get-days-since start-millis) :time) db-values)
        missing-days (into {} (for [day (range total-days) :when (not (avail-days day))]
                            [day [{:time 0 :value 0}]]))
        data-per-day (->> missing-days
                       (merge avail-days)
                       (into (sorted-map))
                       vals
                       (map (partial map :value)))       
        n (bit-shift-left 1 10)
        ffts (vec (pmap #(vec (fft % n)) data-per-day))
        f (fn [x y] (get-in ffts [(int x) (int y)]))
        x-max (count data-per-day)
        y-max (count (first ffts))
        y-max (min y-max 200)
        
        sample-length (- (:time (second db-values)) (:time (first db-values)))
        ]
    (doto (cfj/heat-map f 0 x-max 0 y-max 
                       :color? true
                       :title (format "FFT (n=%d) von %s" n name)
                       :x-label "Tag des Jahres"
                       :y-label "Periodenlänge"
                       :x-step (count data-per-day)
                       :y-step y-max
                       )
      (.. getPlot getRenderer (setBlockWidth 5.0))
      (.. getPlot getRenderer (setBlockHeight 5.0))
      (set-frequency-axis sample-length n y-max)
      ic/view)
    f))

(comment
  (def data 
    (let [name "singwitz.wr.0.pac"
          s (->> "20110601" (.parse (util/dateformatrev)) as-sql-timestamp) 
          e (->> "20110602" (.parse (util/dateformatrev)) as-sql-timestamp)
          db-data (db/all-values-in-time-range name s e)]
      (map :value db-data)))
  
  (ic/view (range (count data)) data)
  (plot-pseudo-wave data (bit-shift-left 1 12))
  (let [n (bit-shift-left 1 12)] (ic/view (ch/xy-plot (range n) (fft (make-cyclic data) n))))
    
  (waterfall "ostfriesenstrom.wr.0.pac" 
             (->> "20100101" (.parse (util/dateformatrev)) .getTime)
             (->> "20111231" (.parse (util/dateformatrev)) .getTime))

  
  )
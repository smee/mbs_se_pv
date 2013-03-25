(ns ^{:doc "Generate json data for client side chart generation"} mbs-se-pv.views.data
  (:require [mbs-se-pv.views.charts :as chart]
            [mbs-se-pv.views.util :as util]
            [mbs-se-pv.algorithms :as alg]
            [mbs-db.core :as db]
            [org.clojars.smee 
             [map :refer (mapp)] 
             [util :refer (s2i s2d s2b)]]
            [noir.response :refer (content-type json)]
            [timeseries.functions :as f]))

(chart/def-chart-page "data.json" []
  (let [width (s2i width nil)
        values (->> names
                 (map #(chart/get-series-values id % s e width))
                 (map (mapp (juxt :timestamp :value)))
                 (map (mapp (fn [[timestamp value]] {:x (long (/ timestamp 1000)), :y value}))))
        all-names (db/all-series-names-of-plant id)]
    (json (map #(hash-map :name (get-in all-names [%1 :name])
                          :type (get-in all-names [%1 :type])
                          :unit (-> %1 chart/get-series-type chart/unit-properties :unit)
                          :key %1 
                          :data %2) names values))))

(defn- insert-nils 
  "Dygraph needs explicit null values if there should be a gap in the chart.
Tries to estimate the average x distance between points and whenever there is a gap bigger than two times
that distance, inserts an artificial entry of shape `[(inc last-x)...]`
Use this function for all dygraph data."
  [data]
  (let [max-gap (->> data (map first) (partition 2 1) (map #(- (second %) (first %))) sort)
        max-gap (if (not-empty max-gap) (* 2 (f/mean max-gap)) max-gap)
        v1 (second (first data))
        nothing (if (coll? v1) (repeat (count v1) nil) nil)] 
    (concat
      (apply concat
             (for [[[x1 & vs] [x2 _]] (partition 2 1 data)
                   :let [diff (- x2 x1)]] 
               (if (> diff max-gap)
                 [(cons x1 vs) (cons (inc x1) (repeat (count vs) nothing))]
                 [(cons x1 vs)])))
      (take-last 1 data))))

(chart/def-chart-page "dygraph.json" [] 
  (let [values (->> names
                 (pmap #(chart/get-series-values id % s e width))
                 (map (mapp #(vector (:timestamp %) [(:min %) (:value %) (:max %)]))))
        ;; if one series has no value for a timestamp, the number of entries in 
        ;; the returned row is too low, need to insert [nil nil nil] instead!
        ; problem: not all lines have the same number of values
        vs-maps (map (partial reduce (fn [m [t vs]] (assoc m t vs)) {}) values)
        timestamps (->> values (apply concat) (map first) distinct sort)
        by-time (for [t timestamps]
                  (apply vector t (map #(get % t [nil nil nil]) vs-maps)))
        all-names (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) names)]
    (json {:labels (cons "Datum" all-names) 
           :data (insert-nils by-time)
           :title (str "Chart für den Zeitraum " (.format (util/dateformat) s) " bis " (.format (util/dateformat) e))}))) ;values

(chart/def-chart-page "dygraph-ratios.json" []
  (let [[num dem] names
        vs (db/rolled-up-ratios-in-time-range id num dem s e width)
        [name1 name2] (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) [num dem])]
    (json {:labels (list "Datum" (str "Verhältnis von " name1 " und " name2)) 
           :data (insert-nils (map (juxt :timestamp :value) vs)) 
           :title (str  "Verhältnis von " name1 " und " name2 "<br/>" (.format (util/dateformat) s) " bis " (.format (util/dateformat) e))})))

;;;;;;;;;;;;;;;;;; relative entropy comparison between daily ratios of time series

(defn- find-indices-over-threshold [threshold vs]
  (keep-indexed #(when (<= threshold %2) %) vs))

(defn- highlight-ranges [threshold n days entropies]
  (let [highlight-indices (set (map #(+ n %) (find-indices-over-threshold threshold entropies)))
        days-to-highlight (keep-indexed #(when (highlight-indices %) %2) days)]
    (map (juxt (comp :timestamp first) (comp :timestamp last)) days-to-highlight)))


;(use 'org.clojars.smee.serialization)
(chart/def-chart-page "entropy.json" [days bins min-hist max-hist threshold skip-missing sensor]
  (let [names (remove #{sensor} names)
        bins (s2i bins 500)
        n (s2i days 30)
        min-hist (s2d min-hist 0.05) 
        max-hist (s2d max-hist 0.2)
        skip-missing (s2b skip-missing) 
;        {:keys [results]} (deserialize (str "d:\\projekte\\EUMONIS\\Usecase PSM Solar\\Daten\\entropies\\20130322\\" (.replaceAll sensor "/" "_") ".clj"))
        results (map #(alg/calculate-entropies id sensor % s e :days n :bins bins :min-hist min-hist :max-hist max-hist) names)
;        res {:results results}
;        _ (serialize (str "d:/Dropbox/temp/" (.replaceAll sensor "/" "_") ".clj") res)
        title (format "Signifikante Veränderungen im Verlauf von \"%s\" (%s)" (get-in all-names [sensor :name]) sensor)
        entropies (map :entropies results)
        ]
    (json {
           :data (insert-nils (apply map vector (-> results first :x) entropies))
           :labels (cons "Datum" (map #(str (get-in all-names [% :component]) "/" (get-in all-names [% :name])) names))
           :highlights [] 
           :title title
           :numerator sensor
           :denominator "all" 
           :threshold threshold
           :stepPlot true})))
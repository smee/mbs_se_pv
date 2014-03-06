(ns ^{:doc "Generate json data for client side chart generation"} mbs-se-pv.views.data
  (:require [mbs-se-pv.views.charts :as chart]
            [mbs-se-pv.views.util :as util :refer [t]]
            [mbs-se-pv.algorithms :as alg]
            [mbs-db.core :as db]
            [clojure.math.combinatorics :as combo]
            [clojure.string :refer [split]]
            [chart-utils.jfreechart :refer [bin-fn]]
            [org.clojars.smee 
             [map :refer (mapp map-values)] 
             [util :refer (s2i s2d s2b)]
             [time :refer [as-sql-timestamp]]]
            [noir.response :refer (content-type json)]
            [noir.core :refer [defpage]]
            [timeseries.functions :as f]))

(defpage "/data/:id/names.json" {:keys [id]}
  (let [all-names (db/all-series-names-of-plant id)]
    (json (map (fn [[id {:keys [name component type]}]] 
                 {:value id,
                  :name name
                  :component component
                  :type type
                  :tokens (distinct (concat [component type] 
                                  (split name #" ")
                                  (split (clojure.core/name id) #"(\.|/)")))}) all-names)))) 

(defn- insert-nils 
  "Dygraph needs explicit null values if there should be a gap in the chart.
Tries to estimate the average x distance between points and whenever there is a gap bigger than two times
that distance, inserts an artificial entry of shape `[(inc last-x)...]`
Use this function for all dygraph data."
  [data]
  (let [gaps (->> data (map first) (partition 2 1) (map #(- (second %) (first %))) sort)
        max-gap (if (not-empty gaps) (* 5 (f/mean gaps)) 0) 
        v1 (second (first data))
        nothing (if (coll? v1) (repeat (count v1) Double/NaN) Double/NaN)] 
    (concat
      (apply concat
             (for [[[x1 & vs] [x2 _]] (partition 2 1 data)
                   :let [diff (- x2 x1)]] 
               (if (> diff max-gap)
                 [(cons x1 vs) (cons (inc x1) (repeat (count vs) nothing))]
                 [(cons x1 vs)])))
      (take-last 1 data))))

(chart/def-chart-page "dygraph.json" [] 
  (let [values (->> (chart/get-series-values id names s e width) 
                 (apply map vector) 
                 (map (mapp #(vector (:timestamp %) [(:min %) (:value %) (:max %)]))))
        ;; if one series has no value for a timestamp, the number of entries in 
        ;; the returned row is too low, need to insert [nil nil nil] instead!
        ; problem: not all lines have the same number of values
        vs-maps (map (partial reduce (fn [m [t vs]] (assoc m t vs)) {}) values)
        timestamps (->> values (apply concat) (map first) distinct sort)
        by-time (for [t timestamps]
                  (apply vector t (map #(get % t [nil nil nil]) vs-maps)))
        labels (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) names)]
    (json {:labels (cons "Datum" labels) 
           :units (cons nil (map #(get-in all-names [%1 :unit]) names)) 
           :data (insert-nils by-time)
           :title (format (t ::line-chart-header) (.format (util/dateformat) s) (.format (util/dateformat) e))}))) ;values

(chart/def-chart-page "dygraph-ratios.json" []
  (let [[num dem] names
        vs (db/all-values-in-time-range id [num dem] s e width
               #(doall (map (fn [[{ts :timestamp v1 :value} {v2 :value}]] {:timestamp ts :value (if (zero? v2) Double/NaN (/ v1 v2))}) %))) 
        [name1 name2] (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) [num dem])]
    (json {:labels (list (t ::date) (format (t ::ratio-chart-title) name1 name2)) 
           :data (insert-nils (map (juxt :timestamp :value) vs))
           :units (cons nil (map #(get-in all-names [%1 :unit]) names))
           :title (format (t ::ratio-chart-header) name1 name2 (.format (util/dateformat) s) (.format (util/dateformat) e))})))

(chart/def-chart-page "dygraph-differences.json" []
  (let [[num dem] names
        vs (alg/calculate-diffs id num dem s e width) 
        [name1 name2] (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) [num dem])]
    (json {:labels (list (t ::date) (format (t ::difference-chart-title) name1 name2)) 
           :data (insert-nils (map (juxt :timestamp :value) vs)) 
           :units (cons nil (map #(get-in all-names [%1 :unit]) names))
           :title (format (t ::difference-chart-header) name1 name2 (.format (util/dateformat) s) (.format (util/dateformat) e))})))

(chart/def-chart-page "relative-heatmap.json" []
  (let [[name1 name2] names
        values (chart/get-series-values id [name1 name2] s e)
        power (map (comp :value first) values)
        wind-speed (map (comp :value second) values)
        [min-p max-p] (apply (juxt min max) power)
        [min-w max-w] (apply (juxt min max) wind-speed)
        ;      [min-p max-p] [0 1000000]
        ;      [min-w max-w] [0 15] 
        x-steps 150
        y-steps 150
        x-bin-width (/ (- max-w min-w) x-steps)
        y-bin-width (/ (- max-p min-p) y-steps)
        data (chart-utils.jfreechart/heat-map-data wind-speed power min-w max-w min-p max-p x-bin-width y-bin-width)] (def data data) 
    (json {:xRange [min-w max-w]
           :yRange [min-p max-p]
           :data (map vec (seq data))})))

;;;;;;;;;;;;;;;;;; relative entropy comparison between daily ratios of time series

(defn series-index [s]
    (let [[_ st inv] (re-find #".*STRING(\d)_MMDC(\d).*" s)
          st (s2i st)
          inv (s2i inv)]
      (+ st (* inv 4))))

(chart/def-chart-page "entropy-bulk.json" [adhoc]
  (if (not-empty adhoc)
    (let [settings (cheshire.core/parse-string adhoc)
          settings (map-values keyword identity settings) _ (println settings)
          {:keys [ids] :as settings} settings
          days (alg/calculate-entropy-matrices-new id s e settings)
          days (alg/add-anomaly-durations days)
          names (for [name ids :let [{label :name device :component} (all-names name)]] (str device "/" label) )]
      (json {:names names
             :ids ids
             :days days}))
    (let [sid (first names)
          ids (-> sid (db/get-scenario) :settings :ids)
          days (db/get-analysis-results id s e sid)
          days (alg/add-anomaly-durations days)
          names (for [name ids :let [{label :name device :component} (all-names name)]] (str device "/" label) )]
      (json {:names names
             :ids ids
             :days days}))))

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
  (let [values (->> names
                 (pmap #(chart/get-series-values id % s e width))
                 (pmap (mapp #(vector (:timestamp %) [(:min %) (:value %) (:max %)]))))
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
        vs (db/rolled-up-ratios-in-time-range id num dem s e width) 
        [name1 name2] (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) [num dem])]
    (json {:labels (list (t ::date) (format (t ::ratio-chart-title) name1 name2)) 
           :data (insert-nils (map (juxt :timestamp :value) vs))
           :units (cons nil (map #(get-in all-names [%1 :unit]) names))
           :title (format (t ::ratio-chart-header) name1 name2 (.format (util/dateformat) s) (.format (util/dateformat) e))})))

(chart/def-chart-page "dygraph-differences.json" []
  (let [[num dem] names
        vs (alg/calculate-diffs id num dem s e) 
        [name1 name2] (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) [num dem])]
    (json {:labels (list (t ::date) (format (t ::difference-chart-title) name1 name2)) 
           :data (insert-nils (map (juxt :timestamp :value) vs)) 
           :title (format (t ::difference-chart-header) name1 name2 (.format (util/dateformat) s) (.format (util/dateformat) e))})))

(chart/def-chart-page "relative-heatmap.json" []
  (let [[name1 name2] names
        [power wind-speed] [(map :value (chart/get-series-values id name1 s e)) 
                            (map :value (chart/get-series-values id name2 s e))] 
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

#_(chart/def-chart-page "histograms.json" []
  (let [histograms (db/all-values-in-time-range plant-id ids s e
                         (fn [slices]
                           ))]))
;;;;;;;;;;;;;;;;;; relative entropy comparison between daily ratios of time series

(use 'org.clojars.smee.serialization)
(chart/def-chart-page "entropy.json" [days bins min-hist max-hist threshold skip-missing sensor]
  (let [names (remove #{sensor} names)
        bins (s2i bins 500)
        n (s2i days 30)
        min-hist (s2d min-hist 0.05) 
        max-hist (s2d max-hist 0.2)
        skip-missing (s2b skip-missing) 
;        {:keys [results]} (deserialize (str "d:\\projekte\\EUMONIS\\Usecase PSM Solar\\Daten\\entropies\\invu1\\20130326 full-days\\" (.replaceAll sensor "/" "_") ".clj"))
        results (pmap #(alg/calculate-entropies id sensor % s e {:n n :bins bins :min-hist min-hist :max-hist max-hist :skip-missing? skip-missing}) names)
;        _ (serialize (str "d:/Dropbox/temp/" (.replaceAll sensor "/" "_") ".clj") {:results results})
        title (format (t ::entropy-chart-title) (get-in all-names [sensor :name]) sensor)
        entropies (map :entropies results)
        ]
    (json {
           :data (insert-nils (apply map vector (-> results first :x) entropies))
           :labels (cons "Datum" (map #(str (get-in all-names [% :component]) "/" (get-in all-names [% :name])) names))
           :title title
           :numerator sensor
           :denominator "all" 
           :threshold threshold
           :stepPlot true})))

(defn series-index [s]
    (let [[_ st inv] (re-find #".*STRING(\d)_MMDC(\d).*" s)
          st (s2i st)
          inv (s2i inv)]
      (+ st (* inv 4))))

(chart/def-chart-page "entropy-bulk.json" [adhoc]
  (if (not-empty adhoc)
    (let [settings (cheshire.core/parse-string adhoc)
          settings (map-values keyword identity settings)
          {:keys [ids] :as settings} (merge {:n 30 :bins 500 :min-hist 0.05 :max-hist 2 :skip-missing? true :threshold 1.3 :use-raw-entropy? true} settings)
          days (alg/calculate-entropy-matrices id s e settings)
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

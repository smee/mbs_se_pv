(ns ^{:doc "Generate json data for client side chart generation"} mbs-se-pv.views.data
  (:require [mbs-se-pv.views.charts :as chart]
            [mbs-se-pv.views.util :as util]
            [mbs-se-pv.algorithms :as alg]
            [mbs-db.core :as db]
            [clojure.math.combinatorics :as combo]
            [clojure.string :refer [split]]
            [org.clojars.smee 
             [map :refer (mapp)] 
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
        e (if (= e s) (+ e util/ONE-DAY) e) 
        vs (db/rolled-up-ratios-in-time-range id num dem s e width)
        [name1 name2] (map #(str (get-in all-names [%1 :component]) "/" (get-in all-names [%1 :name])) [num dem])]
    (json {:labels (list "Datum" (str "Verhältnis von " name1 " und " name2)) 
           :data (insert-nils (map (juxt :timestamp :value) vs)) 
           :title (str  "Verhältnis von " name1 " und " name2 "<br/>" (.format (util/dateformat) s) " bis " (.format (util/dateformat) e))})))

;;;;;;;;;;;;;;;;;; relative entropy comparison between daily ratios of time series

(use 'org.clojars.smee.serialization)
(chart/def-chart-page "entropy.json" [days bins min-hist max-hist threshold skip-missing sensor]
  (let [names (remove #{sensor} names)
        bins (s2i bins 500)
        n (s2i days 30)
        min-hist (s2d min-hist 0.05) 
        max-hist (s2d max-hist 0.2)
        skip-missing (s2b skip-missing) 
        {:keys [results]} (deserialize (str "d:\\projekte\\EUMONIS\\Usecase PSM Solar\\Daten\\entropies\\invu1\\20130326 full-days\\" (.replaceAll sensor "/" "_") ".clj"))
;        results (map #(alg/calculate-entropies id sensor % s e :days n :bins bins :min-hist min-hist :max-hist max-hist :skip-missing? skip-missing) names)
;        _ (serialize (str "d:/Dropbox/temp/" (.replaceAll sensor "/" "_") ".clj") {:results results})
        title (format "Signifikante Veränderungen im Verlauf von \"%s\" (%s)" (get-in all-names [sensor :name]) sensor)
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

(chart/def-chart-page "entropy-bulk.json" [n bins min-hist max-hist] ;; TODO create background job that calculates these scenarios each day
  (let [ids (sort-by series-index names)
        settings (into (sorted-map) 
                       {:n (s2i n 30) 
                        :bins (s2i bins 500 ) 
                        :min-hist (s2d min-hist 0.05) 
                        :max-hist (s2d max-hist 2)
                        :ids ids})
        sid (db/get-scenario-id id settings)
        days (db/get-analysis-results id s e sid)        
        names (for [name ids :let [{label :name device :component} (all-names name)]] (str device "/" label) )]
    
    (json {:names names
           :ids ids
           :days days}))
  )

(comment
  (require '[org.clojars.smee.seq :refer [find-where]])
  (require 'bayesian) 
  (def res (into {} (for [f (file-seq (java.io.File. "d:\\projekte\\EUMONIS\\Usecase PSM Solar\\Daten\\entropies\\invu2\\20130326 full-days\\"))
                          :when (.isFile f)
                          :let [x (:results (deserialize f))]]
                      [(:name (first x)) x])))
  
  (def ps (time (doall (bayesian/calc-failure-probabilities (sort-by series-index (keys res)) res 1.3))))
  
  (defn construct-matrices []
    (let [names (sort-by series-index (keys res))
        all-names  (db/all-series-names-of-plant id)
        pretty-names (for [name names :let [{label :name device :component} (all-names name)]] (str device "/" label) )
        index (into {} (map-indexed #(vector %2 %1) names))
        name-combos (combo/combinations names 2)
        entropies-per-combo (into {} (apply concat
                                            (for [[n1 n2] name-combos] 
                                              [[[n1 n2] (:entropies (find-where #(= (:denominator %) n2) (get res n1)))]
                                               [[n2 n1] (:entropies (find-where #(= (:denominator %) n1) (get res n2)))]])))
        ]
      {:names pretty-names
       :ids names
       :days 
       (for [n (range (-> res first second first :x count))
             :let [date (-> res first second first :x (nth n))]]
         (hash-map :date (.format (util/dateformat) date)
                   :probabilities (for [name names] (-> ps (nth n) (nth (index name))))
                   :entropies (for [a names]
                                (for [b names :let [e (get entropies-per-combo [a b])]]
                                  (when e (nth e n))))))})) 
  (let [result (deserialize "d:\\projekte\\EUMONIS\\Usecase PSM Solar\\Daten\\entropies\\invu1\\scenario-invu1.clj")
        settings (into (sorted-map) {:min-hist 0.05 :bins 500 :max-hist 2 :n 30 :ids (:ids result)})
        {sid :generated_key} (db/insert-scenario "Ourique PV-Anlage" "Stringvergleich INVU1" settings)]
    
    (doseq [day (:days result) :let [date (.parse (util/dateformat) (:date day))]] (def d day)
      (db/insert-scenario-result "Ourique PV-Anlage" date sid day)))
  )
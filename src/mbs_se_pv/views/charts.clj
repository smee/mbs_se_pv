(ns mbs-se-pv.views.charts
  (:require 
    [mbs-se-pv.views.common :as common]
    [mbs-db.core :as db]
    [incanter.core :as ic]
    [incanter.charts :as ch])
  (:use  
    [clojure.string :only (split)]
    [noir.core :only (defpage)]
    [noir.response :only (content-type)]
    [mbs-se-pv.views.util :only (dateformatrev dateformat ONE-DAY)]
    [org.clojars.smee.util :only (s2i)])
  (:import 
    [java.io ByteArrayOutputStream ByteArrayInputStream]
    org.jfree.chart.renderer.xy.StandardXYItemRenderer
    org.jfree.util.UnitType
    java.text.SimpleDateFormat
    java.sql.Timestamp))

(defn- create-renderer
  "do not plot a line when at least 3 values are missing (for example during the night)" 
  []
  (doto (StandardXYItemRenderer.)
    (.setPlotDiscontinuous true)
    (.setGapThresholdType UnitType/RELATIVE)
    (.setGapThreshold 3.0)))

(defn- return-image [chart & opts]
  (let [baos (ByteArrayOutputStream.)]
    (apply ic/save chart baos opts)
    (content-type "image/png" (ByteArrayInputStream. (.toByteArray baos)))))

(defn- parse-times 
  "Parse strings of shape 'yyyyMMdd-yyyyMMdd'."
  [times]
  (when-let [[_ start-time end-time] (re-find #"(\d{8})-(\d{8})" times)]
    (let [s (.getTime (.parse (dateformatrev) start-time))
          e (.getTime (.parse (dateformatrev) end-time))]
      [s e])))

(defn- start-of-day [millis]
  (- millis (mod millis ONE-DAY)))


(defpage "/series-of/:id/*/:times/chart.png" {:keys [id * times width height]}
  (if-let [[s e] (parse-times times)]
    (let [names (re-seq #"[^/]+" *) ;; split at any slashes
          values (map #(db/all-values-in-time-range % (Timestamp. s) (Timestamp. (+ e ONE-DAY))) names)
          chart (ch/time-series-plot (map :time (first values)) (map :value (first values))
                                     :title (str "Chart für den Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e))
                                     :x-label "Zeit"
                                     :y-label "Wert"
                                     :legend true
                                     :series-label (first names))]
      (doseq [[n values] (map list (rest names) (rest values))]
        (ch/add-lines chart (map :time values) (map :value values)
                      :series-label n))
      ;; set renderer that leaves gaps for missing values for all series
      ;; but reuse the series color
      (doseq [i (range (count names)) :let [paint (.. chart getPlot getRenderer (getSeriesPaint i))]]
        (.. chart getPlot (setRenderer i (create-renderer))))
      
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    {:status 500
     :body "Wrong dates!"}))



(defpage draw-efficiency-chart "/series-of/:id/efficiency/:wr-id/:times/chart.png" {:keys [id wr-id times width height]}
  (if-let [[s e] (parse-times times)] 
    (let [pdc (db/summed-values-in-time-range (format "%s.wr.%s.pdc.string.%%" id wr-id) (Timestamp. s) (Timestamp. (+ e ONE-DAY)))
          pac (db/all-values-in-time-range (format "%s.wr.%s.pac" id wr-id) (Timestamp. s) (Timestamp. (+ e ONE-DAY)))
          efficiency (map (fn [a d] (if (< 0 d) (/ a d) 0)) (map :value pac) (map :value pdc))
          chart (doto (ch/time-series-plot (map :time pac) (map (partial * 100) efficiency)
                                           :title (str "Wirkungsgrad für " id wr-id " im Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e))
                                           :x-label "Zeit"
                                           :y-label "Wirkungsgrad in %")
                  (.. getPlot (setRenderer 0 (create-renderer))))] 
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    {:status 500
     :body "Wrong dates!"}))
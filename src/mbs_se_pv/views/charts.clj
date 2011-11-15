(ns mbs-se-pv.views.charts
    (:require [mbs-se-pv.views.common :as common]
            [mbs-se-pv.models.db :as db]
            [incanter.core :as ic]
            [incanter.charts :as ch])
    (:use noir.core
          hiccup.core
          hiccup.page-helpers
          [noir.response :only (content-type)]
          [mbs-se-pv.views.util :only (de-escape-dots)])
    (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
             org.jfree.chart.renderer.xy.StandardXYItemRenderer
             org.jfree.util.UnitType
             java.text.SimpleDateFormat
             java.sql.Timestamp
             ))
(defn- create-renderer
  "do not plot a line when at least 3 values are missing (for example during the night)" 
  []
  (doto (StandardXYItemRenderer.)
                       (.setPlotDiscontinuous true)
                       (.setGapThresholdType UnitType/RELATIVE)
                       (.setGapThreshold 3.0)))

(defn- return-image [chart]
  (let [baos (ByteArrayOutputStream.)]
    (ic/save chart baos)
    (content-type "image/png" (ByteArrayInputStream. (.toByteArray baos)))))

(defn- parse-times [times]
  (let [[_ start-time end-time] (re-find #"(\d{8})-(\d{8})" times)]
    [start-time end-time]))

(defpage draw-daily-chart "/series-of/:id/single/*/:times/chart.png" {:keys [id * times]}
  (let [[start-time end-time] (parse-times times)]
    (if (and start-time end-time) 
      (let [name *
            df (SimpleDateFormat. "yyyyMMdd")
            s (.getTime (.parse df start-time))
            e (.getTime (.parse df end-time))
            values (db/all-values-in-time-range name (Timestamp. s) (Timestamp. e))
            chart (doto (ch/time-series-plot (map :time values) (map :value values)
                                       :title (str "Chart fuer" name " am " (.format df s))
                                       :x-label "Zeit"
                                       :y-label "Wert")
                    (.. getPlot (setRenderer 0 (create-renderer))))] 
        (return-image chart))
      {:status 500
       :body "Wrong dates!"})))

(defpage draw-efficiency-chart "/series-of/:id/efficiency/:wr-id/:times/chart.png" {:keys [id wr-id times]}
  (let [[start-time end-time] (parse-times times)]
    (if (and start-time end-time) 
      (let [df (SimpleDateFormat. "yyyyMMdd")
            s (.getTime (.parse df start-time))
            e (.getTime (.parse df end-time))
            
            pdc (sort-by :time (db/string-values-in-time-range (format "%s.wr.%s.pdc.string.%%" id wr-id) (Timestamp. s) (Timestamp. e)))
            pac (sort-by :time (db/all-values-in-time-range (format "%s.wr.%s.pac" id wr-id) (Timestamp. s) (Timestamp. e)))
            efficiency (map (fn [a d] (if (< 0 a) (/ d a) 0)) (map :value pac) (map :value pdc))
            chart (doto (ch/time-series-plot (map :time pac) efficiency
                                       :title (str "Chart fuer " name " ab " (.format df s))
                                       :x-label "Zeit"
                                       :y-label "Wert")
                    (.. getPlot (setRenderer 0 (create-renderer))))] 
        (return-image chart))
      {:status 500
       :body "Wrong dates!"})))
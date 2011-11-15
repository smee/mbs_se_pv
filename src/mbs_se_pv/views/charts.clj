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


(defpage draw-daily-chart "/series-of/:id/:name/:times/chart.png" {:keys [id name times]}
  (let [[_ start-time end-time] (re-find #"(\d{8})-(\d{8})" times)]
    (if (and start-time end-time) 
      (let [name (de-escape-dots name)
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
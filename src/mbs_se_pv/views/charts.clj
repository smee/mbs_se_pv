(ns mbs-se-pv.views.charts
    (:require [mbs-se-pv.views.common :as common]
            [mbs-se-pv.models.db :as db]
            [incanter.core :as ic]
            [incanter.charts :as ch])
    (:use noir.core
          hiccup.core
          hiccup.page-helpers
          [noir.response :only (content-type)]))


(defpage draw-chart "/details/:times/chart.png" {:keys [name times]}
  (let [[_ start-time end-time] (re-find #"(\d{8})-(\d{8})" times)]
    (if (and start-time end-time) 
      (let [df (java.text.SimpleDateFormat. "yyyyMMdd")
            s (.getTime (.parse df start-time))
            e (.getTime (.parse df end-time))
            values (db/all-values-in-time-range name (java.sql.Timestamp. s) (java.sql.Timestamp. e))
            renderer (doto (org.jfree.chart.renderer.xy.StandardXYItemRenderer.)
                       (.setPlotDiscontinuous true))
            chart (doto (ch/time-series-plot (map :time values) (map :value values)
                                       :title (str "Chart fuer" name " am " (.format df s))
                                       :x-label "Zeit"
                                       :y-label "Wert")
        ;; do not plot a line when values are missing (for example during the night)
                    (.. getPlot (setRenderer 0 renderer)))
            baos (java.io.ByteArrayOutputStream.)] 
        (ic/save chart baos)
        (content-type "image/png" (java.io.ByteArrayInputStream. (.toByteArray baos))))
      {:status 500
       :body "Wrong dates!"})))
(ns mbs-se-pv.views.charts
    (:require [mbs-se-pv.views.common :as common]
            [mbs-se-pv.models.db :as db]
            [incanter.core :as ic]
            [incanter.charts :as ch])
    (:use noir.core
          hiccup.core
          hiccup.page-helpers
          [noir.response :only (content-type)]
          mbs-se-pv.views.util
          [org.clojars.smee.util :only (s2i)])
    (:import [java.io ByteArrayOutputStream ByteArrayInputStream]
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

(defn- parse-times [times]
  (when-let [[_ start-time end-time] (re-find #"(\d{8})-(\d{8})" times)]
    (let [s (.getTime (.parse (dateformatrev) start-time))
          e (.getTime (.parse (dateformatrev) end-time))]
      [s e])))

(defn- start-of-day [millis]
  (- millis (mod millis ONE-DAY)))


(defpage draw-daily-chart "/series-of/:id/single/*/:times/chart.png" {:keys [id * times width height]}
  (if-let [[s e] (parse-times times)] 
    (let [name *
          values (db/all-values-in-time-range name (Timestamp. s) (Timestamp. e))
          chart (doto (ch/time-series-plot (map :time values) (map :value values)
                                           :title (str "Chart für " name " im Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e))
                                           :x-label "Zeit"
                                           :y-label "Wert")
                  (.. getPlot (setRenderer 0 (create-renderer))))]
      (return-image chart :height (s2i height 500) :width (s2i width 400)))
    {:status 500
     :body "Wrong dates!"}))

(defpage chart-modal "/series-of/:id/single/*/:times/chart.htm" {:keys [id * times] :as m}
  (let [name *
        [start end] (parse-times times)
        prev-day-times (format "%s-%s" (.format (dateformatrev) (- start ONE-DAY)) (.format (dateformatrev) start)) 
        next-day-times (format "%s-%s" (.format (dateformatrev) end) (.format (dateformatrev) (+ end ONE-DAY))) 
        prev-day-link (format "$('#chart-popup').load('%s')" (resolve-uri (url-for chart-modal {:id id, :* name, :times prev-day-times} )))
        next-day-link (format "$('#chart-popup').load('%s')" (resolve-uri (url-for chart-modal {:id id, :* name, :times next-day-times} )))] 
    (html
      [:div.modal-header [:a.close {:href "#"} "x"] #_[:h3 "Chart"]]
      [:div#current-chart.modal-body 
       [:img.loading {:src (resolve-uri (url-for draw-daily-chart m))
                      :width "500"
                      :height "400"}]
       #_(javascript-tag)]
      [:div.modal-footer
       [:a.btn.info {:onclick next-day-link} "n&auml;chster Tag →"]
       [:a.btn.info {:onclick prev-day-link} "← vorheriger Tag"]])))

(defpage draw-efficiency-chart "/series-of/:id/efficiency/:wr-id/:times/chart.png" {:keys [id wr-id times width height]}
  (if-let [[s e] (parse-times times)] 
    (let [pdc (db/summed-values-in-time-range (format "%s.wr.%s.pdc.string.%%" id wr-id) (Timestamp. s) (Timestamp. e))
          pac (db/all-values-in-time-range (format "%s.wr.%s.pac" id wr-id) (Timestamp. s) (Timestamp. e))
          efficiency (map (fn [a d] (if (< 0 d) (/ a d) 0)) (map :value pac) (map :value pdc))
          chart (doto (ch/time-series-plot (map :time pac) efficiency
                                           :title (str "Chart fuer " name " ab " (.format (dateformat) s))
                                           :x-label "Zeit"
                                           :y-label "Wert")
                  (.. getPlot (setRenderer 0 (create-renderer))))] 
      (return-image chart :height (s2i height 500) :width (s2i width 400)))
    {:status 500
     :body "Wrong dates!"}))
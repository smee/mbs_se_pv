(ns mbs-se-pv.views.charts
  (:require 
    [clojure.string :as string]
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
    java.sql.Timestamp
    java.awt.Color))

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
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn color-distance 
  "Natural color distance metric, see http:;www.compuphase.com/cmetric.htm"
  [^Color c1 ^Color c2]
  (let [rmean (* 0.5 (+ (.getRed c1) (.getRed c2)))
        r (- (.getRed c1) (.getRed c2))
        g (- (.getGreen c1) (.getGreen c2))
        b (- (.getBlue c1) (.getBlue c2))
        weight-r (+ 2 (/ rmean 256))
        weight-g 4.0
        weight-b (+ 2 (/ (- 255 rmean) 256))]
    (Math/sqrt (+ (* weight-r r r) (* weight-g g g) (* weight-b b b)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-series-type [s]
  (let [parts (->> #"\."
                (string/split s)
                (remove #{"wr" "string"}))]
    (keyword "mbs-se-pv.views.charts" (nth parts 2))))


(defn get-unit-label 
  "Name of the unit type of a time series" 
  [series-name]
  (case (get-series-type series-name)
    (::pac ::pdc) "Leistung P in W"
    ::temp "Temperatur K in °C"
    ::udc "Spannung U in V"
    (::gain ::daily-gain) "Ertrag E in Wh"
    ::efficiency "Wirkungsgrad η in %"
    "Werte"))


(def ^:private base-color {::gain  (Color. 0x803E75) ;Strong Purple
                           ::temp  (Color. 0xFF6800) ;Vivid Orange
                           ::udc   (Color. 0xA6BDD7) ;Very Light Blue
                           ::pac   (Color. 0xC10020) ;Vivid Red
                           ::pdc   (Color. 0xCEA262) ;Grayish Yellow
                           ::efficiency (Color. 0x817066) ;Medium Gray
                           ::daily-gain (Color. 0x817066) ;Medium Gray
                           :default (Color/BLACK)})

(defn- set-axis 
  "Ensure there is an axis for this physical type (power, voltage etc.)"
  [chart series-name series-idx]
  (let [unit-label (get-unit-label series-name)
        p (.. chart getPlot)
        axis-count (.getRangeAxisCount p)
        [idx axis] (or (first (keep-indexed #(let [axis (.. p (getRangeAxis %2))] 
                                               (when (= unit-label (.getLabel axis)) [% axis])) 
                                            (range axis-count)))
                       [axis-count (org.jfree.chart.axis.NumberAxis. unit-label)])
        c (base-color (get-series-type series-name))]
    (do
      (.setLabelPaint axis c)
      (.setAxisLinePaint axis c)
      (.setTickLabelPaint axis c)
      (when (not= axis (.getRangeAxis p idx)) 
        (.setRangeAxis p idx axis))
      (.mapDatasetToRangeAxis p series-idx idx))))

(defn- get-series-label 
  "Unique human readable label."
  [s]
  (let [[_ wr-id _] (->> #"\."
                (string/split s)
                (remove #{"wr" "string"}))
        type (get-series-type s)]
    (format "%s von WR %s" (name type) wr-id)))

(defn- fetch-efficiency 
  "Fetch PAC and PDC values from database, calculate the efficiency, e.g. pac/sum(pdc) per timestamp."
  [id wr-id s e]
  (let [[pdc pac] (pvalues (db/summed-values-in-time-range (format "%s.wr.%s.pdc.string.%%" id wr-id) (Timestamp. s) (Timestamp. (+ e ONE-DAY)))
                           (db/all-values-in-time-range (format "%s.wr.%s.pac" id wr-id) (Timestamp. s) (Timestamp. (+ e ONE-DAY))))
        efficiency (map (fn [a d] (if (< 0 d)  (* 100 (/ a d)) 0)) 
                        (map :value pac) (map :value pdc))]
    (map #(hash-map :time % :value %2) (map :time pac) efficiency)))

;;;;;;;;;;;;;;;; Chart generation pages ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage "/series-of/:id/*/:times/chart.png" {:keys [id * times width height]}
  (if-let [[s e] (parse-times times)]
    (let [names (re-seq #"[^/]+" *) ;; split at any slash
          values (pmap 
                   (fn [n] (let [[id wr-id] (remove #{"wr" "string"} (string/split n #"\."))] 
                             (case (get-series-type n) 
                               ::efficiency (fetch-efficiency id wr-id s e)
                               ::daily-gain (db/max-per-day (str id ".wr." wr-id ".gain") (Timestamp. s) (Timestamp. e))
                               (db/all-values-in-time-range n (Timestamp. s) (Timestamp. (+ e ONE-DAY))))))
                   names)
          chart (ch/time-series-plot (map :time (first values)) (map :value (first values))
                                     :title (str "Chart für den Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e))
                                     :x-label "Zeit"
                                     :y-label (get-unit-label (first names))
                                     :legend true
                                     :series-label (get-series-label (first names)))]
      (doseq [[n values] (map list (rest names) (rest values))]
        (ch/add-lines chart (map :time values) (map :value values)
                        :series-label (get-series-label n)))
      ;; map each time series to a matching axis (one axis per physical type)
      (dorun (map-indexed #(set-axis chart %2 %) names))

      (let [r (create-renderer)]
        (dorun
          (map-indexed
            (fn [i n] (let [r (create-renderer)] ;; set renderer that leaves gaps for missing values for all series
                        (.. chart getPlot (setRenderer i r))
                        ;; set colors
                        (.setSeriesPaint r 0 (base-color (get-series-type n)))))
            names)))
      
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    ;; else the dates format was invalid
    {:status 400
     :body "Wrong dates!"}))


(defpage draw-efficiency-chart "/series-of/:id/efficiency/:wr-id/:times/chart.png" {:keys [id wr-id times width height]}
  (if-let [[s e] (parse-times times)] 
    (let [efficiency (fetch-efficiency id wr-id s e)
          chart (doto (ch/time-series-plot (map :time efficiency) (map :value efficiency)
                                           :title (str "Wirkungsgrad für " id wr-id " im Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e))
                                           :x-label "Zeit"
                                           :y-label "Wirkungsgrad in %")
                  (.. getPlot (setRenderer 0 (create-renderer))))]
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    ;; else the dates format was invalid
    {:status 400
     :body "Wrong dates!"}))

(defpage "/series-of/:id/gain-per-day/:times/chart.png" {:keys [id wr-id times width height]}
  (if-let [[s e] (parse-times times)] 
    (let [data (db/max-per-day (str id ".wr." wr-id ".gain") (Timestamp. s) (Timestamp. e))
          chart (doto (ch/bar-chart 
                        (map #(.format (dateformat) (:time %)) data) (map #(/ (:value %) 1000) data)
                        :title (format "Erträge für %s WR %s im Zeitraum %s bis %s" id wr-id (.format (dateformat) s) (.format (dateformat) e))
                        :x-label "Zeit"
                        :y-label "Ertrag in kWh"))] 
      (doto (.. chart getPlot getDomainAxis)
        (.setCategoryLabelPositions org.jfree.chart.axis.CategoryLabelPositions/UP_90))
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    ;; else the dates format was invalid
    {:status 400
     :body "Wrong dates!"}))
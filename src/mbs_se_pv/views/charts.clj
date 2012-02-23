(ns mbs-se-pv.views.charts
  (:require 
    [clojure.string :as string]
    [mbs-se-pv.views.common :as common]
    [mbs-db.core :as db]
    [incanter.core :as ic]
    [incanter.charts :as ch]
    [timeseries.discord :as discord])
  (:use  
    [clojure.string :only (split)]
    [noir.core :only (defpage)]
    [noir.response :only (content-type)]
    [mbs-se-pv.views.util :only (dateformatrev dateformat ONE-DAY convert-si-unit create-si-prefix-formatter)]
    [org.clojars.smee.util :only (s2i)])
  (:import 
    [java.io ByteArrayOutputStream ByteArrayInputStream]
    org.jfree.chart.axis.AxisLocation
    org.jfree.chart.axis.CategoryLabelPositions
    org.jfree.chart.renderer.xy.StandardXYItemRenderer
    org.jfree.util.UnitType
    java.text.SimpleDateFormat
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
      [(min s e) (max s e)])))

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

(def ^:private unit-properties 
  {::pac        {:color (Color. 0xC10020) :unit "W" :label "Leistung"} 
   ::pdc        {:color (Color. 0xC10020) :unit "W" :label "Leistung"}
   ::temp       {:color (Color. 0xFF6800) :unit "°C" :label "Temperatur"}
   ::udc        {:color (Color. 0xA6BDD7) :unit "V" :label "Spannung"}
   ::gain       {:color (Color. 0x803E75) :unit "Wh" :label "Ertrag"} 
   ::daily-gain {:color (Color. 0x803E75) :unit "Wh" :label "Ertrag"}
   ::efficiency {:color (Color. 0x817066) :unit "%" :label "Wirkungsgrad"}})

(defn- get-series-label 
  "Unique human readable label."
  [s]
  (let [[_ wr-id] (re-find #".*\.wr\.(\d+)\..*" s )
        type (get-series-type s)]
    (format "%s von WR %s" (-> s get-series-type unit-properties :label) wr-id)))


(defn- get-series-values 
  "Call appropriate database queries according to (get-series-type series-name). Returns
sequence of value sequences (seq. of maps with keys :time and :value)."
  [series-name start-time end-time]
  (let [[id wr-id] (remove #{"wr" "string"} (string/split series-name #"\."))
        s (db/as-sql-timestamp start-time) 
        e(db/as-sql-timestamp (+ end-time ONE-DAY))]
    (case (get-series-type series-name) 
      ::efficiency (map (fn [m] (update-in m [:value] min 100)) (db/get-efficiency id wr-id s e));; FIXME data should never go over 100%!
      ::daily-gain (db/sum-per-day (str id ".wr." wr-id ".gain") s e)
      (db/all-values-in-time-range series-name s e))))

;;;;;;;;;;;;;;;; Functions for rendering nice physical time series data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- set-axis 
  "Ensure there is an axis for this physical type (power, voltage etc.). Sets a unique color per unit. 
Distributes all axis so there is a roughly equal number of axes on each side of the chart."
  [chart series-name series-idx]
  (let [props (unit-properties (get-series-type series-name))
        axis-label (:label props)
        c (:color props)
        p (.. chart getPlot)
        axis-count (.getRangeAxisCount p)
        [idx axis] (or (first (keep-indexed 
                                #(let [axis (.. p (getRangeAxis %2))] 
                                   (when (= axis-label (.getLabel axis)) [% axis])) 
                                (range axis-count)))
                       [axis-count (org.jfree.chart.axis.NumberAxis. axis-label)])]
    (doto axis
      (.setLabelPaint c)
      (.setAxisLinePaint c)
      (.setTickLabelPaint c)
      (.setNumberFormatOverride (create-si-prefix-formatter "#.##"  (:unit props))))
    (when (not= axis (.getRangeAxis p idx)) 
      (.setRangeAxis p idx axis))
    (.mapDatasetToRangeAxis p series-idx idx)
    (doseq [n (range (.getRangeAxisCount p)) :let [axis (.. p (getRangeAxis n))
                                                   pos (if (odd? n) AxisLocation/TOP_OR_LEFT AxisLocation/BOTTOM_OR_RIGHT)]]
      (.setRangeAxisLocation p n pos))))

(defn- enhance-chart 
  "Make the chart look nice by introducing axes according to physical types, colors etc."
  [chart names]
  ;; map each time series to a matching axis (one axis per physical type)
  (dorun (map-indexed #(set-axis chart %2 %) names))
  ;; set renderer that leaves gaps for missing values for all series
  (dorun
    (map-indexed
      (fn [i n] 
        (let [r (create-renderer)] 
          (.. chart getPlot (setRenderer i r))
          ;; set colors
          (.setSeriesPaint r 0 (-> n get-series-type unit-properties :color))))
      names))
  chart)

(defn- create-time-series-chart 
  "Create a new time series chart and add all series."
  ([names series] (create-time-series-chart names series get-series-label))
  ([names series label-fn ]
  (let [[name1 & names] names
        [val1 & series] series 
        chart (ch/time-series-plot 
                (map :time val1) 
                (map :value val1)
                :legend true
                :series-label (label-fn name1))]
    ;; plot each time series to chart
    (doseq [[name series] (map list names series)]
        (ch/add-lines chart 
                      (map :time series) 
                      (map :value series)
                      :series-label (label-fn name)))
    chart)))


;;;;;;;;;;;;;;;; Chart generation pages ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage "/series-of/:id/*/:times/chart.png" {:keys [id * times width height]}
  (if-let [[s e] (parse-times times)]
    (let [names (distinct (re-seq #"[^/]+" *)) ;; split at any slash
          values (pmap #(get-series-values % s e) names)]
      
      (-> (create-time-series-chart names values)
        (ch/set-x-label "Zeit")
        (ch/set-y-label (-> names first get-series-type unit-properties :label))
        (ch/set-title (str "Chart für den Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e)))
        (enhance-chart names)
        (return-image :height (s2i height 500) :width (s2i width 600))))
    ;; else the dates format was invalid
    {:status 400
     :body "Wrong dates!"}))

(defn- day-number [{millis :time}]
  (int (/ millis (* 1000 60 60 24))))

;; show day that represents the biggest discord of a given series.
(defpage "/series-of/:id/*/:times/discord.png" {:keys [id * times width height num]}
  (if-let [[s e] (parse-times times)]
    (let [num (s2i num 1)
          name (first (re-seq #"[^/]+" *))
          data (get-series-values name s e)
          days (partition-by day-number data)
          _ (println "got " (count days) " days in " times)
          discords (discord/find-multiple-discords-daily (map (partial map :value) days) num)
          discord-days (->> discords
                         (map #(nth days (first %)))
                         (map vec)
                         (map (fn [day] (map #(update-in % [:time] mod ONE-DAY) day))))]
      (-> (create-time-series-chart 
            (for [[idx v] discords] (format "Discord am %s: %f" (.format (dateformat) (-> days (nth idx) first :time)) v)) 
            discord-days 
            str)
        (ch/set-x-label "Zeit")
        (ch/set-y-label (-> name get-series-type unit-properties :label))
        (ch/set-title (str "Die ungewöhnlichsten Tage"))
        (enhance-chart [name])
        (return-image :height (s2i height 500) :width (s2i width 600))))
    ;; else the dates format was invalid
  {:status 400
   :body "Wrong dates!"}))

;; show summed gain as bar charts for days, weeks, months, years
(defpage "/gains/:id/:times/chart.png" {:keys [id wr-id times width height unit]}
  (if-let [[s e] (parse-times times)] 
    (let [db-query (case unit 
                     "day" db/sum-per-day, 
                     "week" db/sum-per-week, 
                     "month" db/sum-per-month, 
                     "year" db/sum-per-year, 
                     db/sum-per-day)
          name (str id ".wr." (if wr-id wr-id "%") ".gain")
          data (db-query name (db/as-sql-timestamp s) (db/as-sql-timestamp e))
          chart (doto (ch/bar-chart 
                        (map #(.format (dateformat) (:time %)) data) (map #(/ (:value %) 1000) data)
                        ;:title (format "Gesamtertrag für %s WR %s im Zeitraum %s bis %s" id wr-id (.format (dateformat) s) (.format (dateformat) e))
                        :x-label "Zeit"
                        :y-label "Ertrag in kWh"))] 
      (doto (.. chart getPlot getDomainAxis)
        (.setCategoryLabelPositions org.jfree.chart.axis.CategoryLabelPositions/UP_90))
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    ;; else the dates format was invalid
    {:status 400
     :body "Wrong dates!"}))

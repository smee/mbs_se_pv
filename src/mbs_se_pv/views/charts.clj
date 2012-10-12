(ns mbs-se-pv.views.charts
  (:require 
    [clojure.string :as string]
    [mbs-se-pv.views 
     [common :as common]
     [util :as util]]
    [mbs-db.core :as db]
    [incanter.core :as ic]
    [incanter.charts :as ch]
    [timeseries 
     [discord :as discord]
     [correlations :as tc]]
    [chart-utils.jfreechart :as cjf])
  (:use  
    timeseries.align
    [clojure.string :only (split)]
    [noir.core :only (defpage)]
    [noir.response :only (content-type json)]
    [mbs-se-pv.views.util :only (dateformatrev dateformat ONE-DAY convert-si-unit create-si-prefix-formatter)]
    [org.clojars.smee 
     [map :only (mapp)] 
     [time :only (as-sql-timestamp as-date as-unix-timestamp)]
     [util :only (s2i)]])
  (:import 
    [java.io ByteArrayOutputStream ByteArrayInputStream]
    org.jfree.chart.axis.AxisLocation
    org.jfree.chart.axis.CategoryLabelPositions
    org.jfree.chart.renderer.xy.StandardXYItemRenderer
    org.jfree.util.UnitType
    java.text.SimpleDateFormat
    java.awt.Color))

;; implement incanter.core/save for BufferedImages
(defmethod ic/save java.awt.image.BufferedImage
  ([img filename-or-stream & options]
    ;; if filename is not a string, treat it as java.io.OutputStream
    (if (string? filename-or-stream)
      (org.jfree.chart.ChartUtilities/writeBufferedImageAsPNG (java.io.File. filename-or-stream) img)
      (javax.imageio.ImageIO/write img "png" filename-or-stream))
    nil))

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
    (noir.response/set-headers 
        {"Cache-Control" "public, max-age=31536000, s-maxage=31536000"}
        (content-type "image/png" (ByteArrayInputStream. (.toByteArray baos))))))

(defn- parse-times 
  "Parse strings of shape 'yyyyMMdd-yyyyMMdd'."
  [times]
  (when-let [[_ start-time end-time] (re-find #"(\d{8})-(\d{8})" times)]
    (let [s (as-unix-timestamp (.parse (dateformatrev) start-time))
          e (as-unix-timestamp (.parse (dateformatrev) end-time))]
      [(min s e) (max s e)])))

(defn- start-of-day [millis]
  (- millis (mod millis ONE-DAY)))


;;;;;;;;;;;;;;;; experiments with colors and color scales ;;;;;;;;;;;;;;;;;;
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

(def efficiency-colors
  [[0 (java.awt.Color. 0xff1e00)]
   [15 (java.awt.Color. 0xff3c00)]
   [30 (java.awt.Color. 0xff5a00)]
   [40 (java.awt.Color. 0xff7800)]
   [50 (java.awt.Color. 0xff9600)]
   [60 (java.awt.Color. 0xffb400)]
   [70 (java.awt.Color. 0xffd200)]
   [80 (java.awt.Color. 0xfff000)]
   [81 (java.awt.Color. 0xfff300)]
   [82 (java.awt.Color. 0xfff600)]
   [83 (java.awt.Color. 0xfff900)]
   [84 (java.awt.Color. 0xfffc00)]
   [85 (java.awt.Color. 0xffff00)]
   [86 (java.awt.Color. 0xeef700)]
   [87 (java.awt.Color. 0xddee00)]
   [88 (java.awt.Color. 0xcce600)]
   [89 (java.awt.Color. 0xbbdd00)]
   [90 (java.awt.Color. 0xaad500)]
   [91 (java.awt.Color. 0x99cc00)]
   [92 (java.awt.Color. 0x88c400)]
   [93 (java.awt.Color. 0x77bb00)]
   [94 (java.awt.Color. 0x66b300)]
   [95 (java.awt.Color. 0x55aa00)]
   [96 (java.awt.Color. 0x44a200)]
   [97 (java.awt.Color. 0x339900)]
   [98 (java.awt.Color. 0x229100)]
   [99 (java.awt.Color. 0x118800)]])
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-series-type [s]
  (let [parts (->> s
                (remove #(Character/isDigit %))
                (partition-by #{\. \/})  
                (take-nth 2)
                (map (partial apply str))
                (drop 2) ;; device name, ln name
                (string/join "."))]
    (keyword "mbs-se-pv.views.charts" parts)))

(defn- unit-properties [val] 
  (let[m1 {::A.phsA.cVal ::curr
           ::A.phsB.cVal ::curr
           ::A.phsC.cVal ::curr
           ::Amp.mag.f ::curr
           ::EnclTmp.mag.f ::temp
           ::EnvTmp.mag.f ::temp
           ::HeatSinkTmp.mag.f ::temp
           ::HorInsol.mag.f ::ins
           ::Hz.mag.f ::freq
           ::PhV.phsA.cVal ::udc
           ::PhV.phsB.cVal ::udc
           ::PhV.phsC.cVal ::udc
           ::Ris.mag.f ::res
           ::RotSpd.mag.f ::rpm
           ::TotVA.mag.f ::pac
           ::TotVAr.mag.f ::pac
           ::TotW.mag.f ::pac
           ::TotWh.actVal ::gain
           ::TmpSv.instMag ::temp
           ::Vol.mag.f ::udc
           ::Watt.mag.f ::pac
           ::W.net.instCVal.mag.f ::pac}  
       m2 {::pac        {:color (Color. 0xC10020) :unit "W" :label "Leistung"} 
           ::pdc        {:color (Color. 0xC10020) :unit "W" :label "Leistung"}
           ::temp       {:color (Color. 0xFF6800) :unit "°C" :label "Temperatur"}
           ::udc        {:color (Color. 0xA6BDD7) :unit "V" :label "Spannung"}
           ::res        {:color (Color. 0x0000a0) :unit "Ohm" :label "Widerstand"}
           ::ins        {:color (Color. 0xAAAA00) :unit "W/m²" :label "Einstrahlung"}
           ::freq       {:color (Color. 0x111111) :unit "Hz" :label "Frequenz"}
           ::curr       {:color Color/RED         :unit "A" :label "Stromstärke"}
           ::gain       {:color (Color. 0x803E75) :unit "Wh" :label "Ertrag"} 
           ::daily-gain {:color (Color. 0x803E75) :unit "Wh" :label "Ertrag"}
           ::rpm        {:color (Color/BLUE)      :unit "rpm" :label "Umdrehungen"} 
           ::efficiency {:color (Color. 0x817066) :unit "%" :label "Wirkungsgrad" :min 0 :max 99.999}}]
    (m2 (m1 val) {:color (Color/BLACK) :unit "???" :label "unbekannte Größe"})))

(defn- get-series-label-solarlog 
  "Unique human readable label."
  [s]
  (let [[_ wr-id] (re-find #".*\.wr\.(\d+)\..*" s )
        type (get-series-type s)]
    (format "%s von%s" (-> s get-series-type unit-properties :label) wr-id)))

(defn- get-series-label-iec61850 [s]
  (format "%s von %s" (-> s get-series-type unit-properties :label) s))

(defn- get-series-values 
  "Call appropriate database queries according to (get-series-type series-name). Returns
sequence of value sequences (seq. of maps with keys :timestamp and :value)."
  ([plant-name series-id start-time end-time] (get-series-values plant-name series-id start-time end-time nil))
  ([plant-name series-id start-time end-time width]
    (let [s (as-sql-timestamp start-time) 
          e (as-sql-timestamp (+ end-time ONE-DAY))]
      (if width
        (db/rolled-up-values-in-time-range plant-name series-id s e width)
        (db/all-values-in-time-range plant-name series-id s e)))))

;;;;;;;;;;;;;;;; Functions for rendering nice physical time series data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- set-axis 
  "Ensure there is an axis for this physical type (power, voltage etc.). Sets a unique color per unit. 
Distributes all axis so there is a roughly equal number of axes on each side of the chart."
  [chart series-name series-idx]
  (let [font (java.awt.Font. "SansSerif" 0 10)
        props (unit-properties (get-series-type series-name))
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
      (.setLabelFont font) 
      (.setTickLabelFont font) 
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
  (.. chart getLegend (setItemFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 7))) 
  chart)

(defn- create-time-series-chart 
  "Create a new time series chart and add all series."
  ([names series] (create-time-series-chart names series get-series-label-iec61850))
  ([names series label-fn]
    (let [[name1 & names] names
          [val1 & series] series 
          chart (ch/time-series-plot 
                  (map :timestamp val1) 
                  (map :value val1)
                  :legend true
                  :series-label (label-fn name1))]
      ;; plot each time series to chart
      (doseq [[name series] (map list names series)]
        (ch/add-lines chart 
                      (map :timestamp series) 
                      (map :value series)
                      :series-label (label-fn name)))
      chart)))


;;;;;;;;;;;;;;;; Chart generation pages ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage "/series-of/:id/*/:times/chart.png" {:keys [id * times width height]}
  (if-let [[s e] (parse-times times)]
    (let [names (distinct (re-seq #"[^-]+" *)) ;; split at any minus
          ;names (sort (map first (db/all-series-names-of-plant id))) ; render all available series :)
          values (pmap #(get-series-values id % s e) names)]
      
      (-> (create-time-series-chart names values)
        (ch/set-x-label "Zeit")
        (ch/set-y-label (-> names first get-series-type unit-properties :label))
        (ch/set-title (str "Chart für den Zeitraum " (.format (dateformat) s) " bis " (.format (dateformat) e)))
        (enhance-chart names)
        (return-image :height (s2i height 500) :width (s2i width 600))))
    ;; else the dates format was invalid
    {:status 400
     :body "Wrong dates!"}))

(defn- day-number [{millis :timestamp}]
  (int (/ millis (* 1000 60 60 24))))

;; show day that represents the biggest discord of a given series.
(defpage "/series-of/:id/*/:times/discord.png" {:keys [id * times width height num]}
  (if-let [[s e] (parse-times times)]
    (let [num (s2i num 1)
          name *
          data (get-series-values id name s e)
          days (partition-by day-number data)
          discords (discord/find-discords-in-seqs (map (partial map :value) days) num)
          discord-days (->> discords
                         (map #(nth days (first %)))
                         (map vec)
                         (map (fn [day] (map #(update-in % [:timestamp] mod ONE-DAY) day))))]
      (-> (create-time-series-chart 
            (for [[idx v] discords] (format "Discord am %s: %f" (.format (dateformat) (-> days (nth idx) first :timestamp)) v)) 
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
(defpage gain-chart "/gains/:id/:times/chart.png" {:keys [id times width height unit]}
  (if-let [[s e] (parse-times times)] 
    (let [db-query (case unit 
                     "day" db/sum-per-day, 
                     "week" db/sum-per-week, 
                     "month" db/sum-per-month, 
                     "year" db/sum-per-year, 
                     db/sum-per-day)
          name id
          data (db-query name (as-sql-timestamp s) (as-sql-timestamp e))
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

;;;;;;;;;;;;;;;; heat map overview ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- hm [hours minutes]
  (* 60 1000 (+ minutes (* hours 60))))

(defn- insert-missing-days [start-date end-date days]
  (let [all-day-numbers (range (day-number {:timestamp start-date}) (day-number {:timestamp end-date}))
        m (reduce #(assoc % %2 []) (sorted-map) all-day-numbers)
        m (reduce #(assoc % (day-number (first %2)) %2) m days)]
    (vals m)))

(defpage "/series-of/:id/*/:times/heat-map.png" {:keys [id * times width height hours minutes]}
  (if-let [[s e] (parse-times times)]
    (let [name (first (distinct (re-seq #"[^-]+" *))) ;; split at any slash
          values (get-series-values id name s e)
          days (->> values (partition-by day-number) (insert-missing-days s e)) 
          daily-start (hm 6 0)
          daily-end (hm 22 0)
          five-min (hm (s2i hours 0) (s2i minutes 5))
          gridded (map #(smooth (into-time-grid (map (juxt :timestamp :value) %) daily-start daily-end five-min)) days)
          days (vec (map #(vec (map second %)) gridded))
          x-max (count days )
          y-max (apply max (map count days))
          f (fn [x y] 
              (if-let [v (get-in days [(int (/ (- x s) ONE-DAY)) (int (/ (- y daily-start) five-min))])]
                v
                0))
          chart (doto (cjf/heat-map f s (+ s (clojure.core/* ONE-DAY x-max)) daily-start daily-end 
                                    :color? true
                                    :title (format "Tagesverläufe von %s im Zeitraum %s" name times)
                                    :x-label "Tag"
                                    :y-label "Werte"
                                    :z-label (-> name get-series-type unit-properties :label) 
                                    :x-step (count days)
                                    :y-step y-max
                                    :z-min (-> name get-series-type unit-properties :min)
                                    :z-max (-> name get-series-type unit-properties :max)
                                    :colors (when (= ::efficiency (get-series-type name)) efficiency-colors)
                                    :color? true)
                  (.. getPlot getRenderer (setBlockWidth ONE-DAY))
                  (.. getPlot getRenderer (setBlockHeight five-min)))]
      (.. chart getPlot (setDomainAxis (org.jfree.chart.axis.DateAxis.)))
      (.. chart getPlot (setRangeAxis (org.jfree.chart.axis.DateAxis.)))
      (return-image chart :height (s2i height 500) :width (s2i width 600)))
    {:status 400
     :body "Wrong dates!"}))

(defpage "/series-of/:id/*/:times/data.json" {:keys [id * times width]}
  (if-let [[s e] (parse-times times)]
    (let [names (distinct (re-seq #"[^-]+" *)) ;; split at any slash
          width (s2i width nil)
          values (->> names
                   (map #(get-series-values id % s e width))
                   (map (mapp (juxt :timestamp :value)))
                   (map (mapp (fn [[timestamp value]] {:x (long (/ timestamp 1000)), :y value}))))
          all-names (db/all-series-names-of-plant id)]
      (json (map #(hash-map :name (get all-names %1) :key %1 :data %2) names values)
        #_{:title (str "Betriebsdaten im Zeitraum " times)
         :x-label "Zeit"
         :series (map #(let [type (get-series-type %)
                             v %2
                             {:keys [unit label]} (get unit-properties type)] 
                         (hash-map :name %, :unit unit, :label label, :type type, :data %2)) 
                      names values)}))
    {:status 400
     :body "Wrong dates!"}))

;;;;;;;;;;;; render a tiling map of a chart ;;;;;;;;;;;;;;;;;;;;;;
(defn- render-grid [len x y zoom]
  (let [bi (java.awt.image.BufferedImage. len len java.awt.image.BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics bi)
        baos (ByteArrayOutputStream.)]
    (doto g
      (.setColor Color/RED)
      (.drawRect 0 0 (dec len) (dec len)) 
      (.drawString (format "[%d,%d] @ %d" x y zoom) 20 20))
    (javax.imageio.ImageIO/write bi "png" baos)
    (content-type "image/png" (ByteArrayInputStream. (.toByteArray baos)))))

(defn- hide-legend [chart]
  (.removeLegend chart)
  chart)

(defn- hide-axis [chart]
  (dotimes [i (.. chart getPlot getRangeAxisCount)] 
    (doto (.. chart getPlot (getRangeAxis i))
      (.setVisible false)
      (.setLowerMargin 0)
      (.setUpperMargin 0)))
  (doto (.. chart getPlot getDomainAxis)
    (.setVisible false)
    (.setLowerMargin 0)
    (.setUpperMargin 0))  
  (doto (.getPlot chart)
    (.setDomainGridlinesVisible false)
    (.setRangeGridlinesVisible false)
    (.setRangeMinorGridlinesVisible false)
    (.setOutlineVisible false)
    (.setInsets org.jfree.ui.RectangleInsets/ZERO_INSETS)
    (.setRangeTickBandPaint nil)
    (.setDomainZeroBaselineVisible false))
  (doto chart 
    (.setBorderVisible false)
    hide-legend
    (.setTitle nil)
    (.setPadding org.jfree.ui.RectangleInsets/ZERO_INSETS))  
  
  chart)

(defpage "/tiles/:id/*/:times/:x/:y/:zoom" {:keys [id times x y zoom] :as params}
  (if-let [[s e] (parse-times times)]
    (let [names (distinct (re-seq #"[^-]+" (get params :*))) ;; split at any minus
          values (pmap #(get-series-values id % s e) names)
          chart (create-time-series-chart names values)
          x (s2i x), y (s2i y), zoom (s2i zoom)
          ;TODO find true maximum y for all series!
          xmin (apply min (map :timestamp (first values)))
          xmax (apply max (map :timestamp (first values)))
          x-range (- xmax xmin)
          ymin (reduce min (mapcat (partial map :value) values))
          ymax (reduce max (mapcat (partial map :value) values))
          y-range (- ymax ymin)
          num-tiles (bit-shift-left 1 zoom)
          tile-x-len (long (/ x-range num-tiles)) ;x are timestamps in milliseconds, always a long
          tile-y-len (/ y-range num-tiles) ; y are floating point values, seldom a long (so do not cast!)
          tile-xmin (+ xmin (* x tile-x-len))
          tile-xmax (+ xmin (* (inc x) tile-x-len))
          tile-ymin (- ymax (* (inc y) tile-y-len))
          tile-ymax (- ymax (* y tile-y-len))]
      ;(println x y zoom)
      ;(printf "tile-x: [%d; %d], tile-y: [%.2f; %.2f]\n" tile-xmin tile-xmax tile-ymin tile-ymax)
      ;(printf "tile-x-len=%d, tile-y-len=%.2f\n" tile-x-len tile-y-len)
      ;(println (apply str (repeat 50 "-")))
      ;; draw square / grid
      ;(render-grid 256 x y zoom)
      (noir.response/set-headers 
        {"Cache-Control" "public, max-age=60, s-maxage=60"}
        (-> chart
          ;(enhance-chart names)
          (ch/set-x-range (as-date tile-xmin) (as-date tile-xmax))
          (cjf/set-y-ranges tile-ymin tile-ymax)
          ;hide-legend
          hide-axis
          return-image)))
    {:status 400
   :body "Wrong dates!"}))

(defpage "/map" []
  (hiccup.core/html
    (hiccup.page/include-css "http://cdn.leafletjs.com/leaflet-0.4/leaflet.css")
    (hiccup.page/include-js "http://cdn.leafletjs.com/leaflet-0.4/leaflet.js")
    [:div#map {:style "height: 800px;"}]
    (hiccup.element/javascript-tag
      (str 
        "var map = L.map('map').setView([51.505, -0.09], 1);
       L.tileLayer('" (util/base-url) "/tiles/Ourique%20PV-Anlage/INVU1/MMDC0.Watt.mag.f/20110822-20110825/{x}/{y}/{z}', {
    attribution: 'done by me :)',
    noWrap: true,
    tileSize: 256,
    maxZoom: 10
}).addTo(map);"))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;; render a correlation matrix plot ;;;;;;;;;;;;;;;;
(defpage "/series-of/:id/*/:times/correlation.png" {:keys [id * times]}
  (if-let [[s e] (parse-times times)]
    (let [names (distinct (re-seq #"[^-]+" *)) ;; split at any slash
          values (for [name names] (map :value (get-series-values id name s (+ e ONE-DAY)))) 
          img (-> values 
                tc/calculate-correlation-matrix 
                (tc/render-frame names (.format (dateformat) s)))]
      (return-image img))
    {:status 400
     :body "Wrong dates!"}))



(comment 
  (def vs (get-series-values "Ourique PV-Anlage" "INVU1/MMET1.HorInsol.mag.f" (as-unix-timestamp #inst "2012-07-15") (as-unix-timestamp #inst "2012-07-23")))
  (def days (->> vs (partition-by #(int (/ (:timestamp %) (* 1000 60 60 24)))) (map #(map :value %))))
  (def diffs (map (fn [vs] (map (fn [[a b]] (- b a)) (partition 2 1 vs))) days))
  (let [m (apply max (map count diffs))
      c (ch/xy-plot (range m) (repeat 0))] 
  
  (doseq [day diffs #_(take 5 (map #(drop-while (partial > 30) %) diffs))] 
    (ch/add-lines c (range (count day)) (timeseries.functions/ema 0.05 day)))
  (ic/view c))
  )
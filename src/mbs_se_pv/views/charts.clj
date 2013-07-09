(ns ^{:doc "Generate static graphics"} mbs-se-pv.views.charts
  (:require
    [clojure.string :as string]
    [mbs-se-pv.views.util :as util]
    [mbs-db.core :as db]
    [incanter 
     [core :as ic]
     [charts :as ch]]  
    [chart-utils.jfreechart :as cjf]
    [noir
     [core :refer [defpage]]
     [response :as response]]
    [timeseries
     [align :as align]
     [discord :as discord]
     [correlations :as tc]
     [changepoints :as cp]
     [functions :as f]])
  (:use  
    [org.clojars.smee 
     [time :only (as-sql-timestamp as-unix-timestamp)]
     [util :only (s2i s2d)]])
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


(defn- return-image [chart & opts] 
  (let [baos (ByteArrayOutputStream.)]
    (apply ic/save chart baos opts)
    (response/set-headers 
        {"Cache-Control" "public, max-age=31536000, s-maxage=31536000"}
        (response/content-type "image/png" (ByteArrayInputStream. (.toByteArray baos))))))

(defn parse-times 
  "Parse strings of shape 'yyyyMMdd-yyyyMMdd'."
  [times]
  (when-let [[_ start-time end-time] (re-find #"(\d{8,12})-(\d{8,12})" times)]
    (let [^SimpleDateFormat format (if (= 8 (count start-time)) (util/dateformatrev) (util/dateformatrev-detailed))
          s (as-unix-timestamp (.parse format start-time))
          e (as-unix-timestamp (.parse format end-time))]
      [(min s e) (max s e)])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-series-type [s]
  (let [parts (->> s
                (remove #(Character/isDigit %))
                (partition-by #{\. \/})  
                (take-nth 2)
                (map (partial apply str))
                (drop 2) ;; device name, ln name
                (string/join "."))]
    (keyword "mbs-se-pv.views.charts" parts)))

(defn unit-properties [val] 
  (let[m1 {::A.phsA.cVal ::curr
           ::A.phsB.cVal ::curr
           ::A.phsC.cVal ::curr
           ::Amp.mag.f ::curr
           ::ConvEfc.mag.f ::efficiency
           ::EnclTmp.mag.f ::temp
           ::EnvTmp.mag.f ::temp
           ::HeatSinkTmp.mag.f ::temp
           ::HorInsol.mag.f ::ins
           ::Hz.mag.f ::freq
           ::PhV.phsA.cVal ::udc
           ::PhV.phsB.cVal ::udc
           ::PhV.phsC.cVal ::udc
           ::PowRat.mag.f ::efficiency
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
           ::freq       {:color (Color. 0x111111) :unit "Hz" :label "Frequenz" :min 49.9 :max 50.1 
                         :color-scale (cjf/fixed-color-scale (cjf/create-color-scale [49.9 [0 0 255]] [50 [0 255 0]] [50.05 [255 255 0]] [50.1 [255 0 0]]) (range 49.9 51.1 (/ 0.2 30)))}
           ::curr       {:color Color/RED         :unit "A" :label "Stromstärke"}
           ::gain       {:color (Color. 0x803E75) :unit "Wh" :label "Ertrag"} 
           ::daily-gain {:color (Color. 0x803E75) :unit "Wh" :label "Ertrag"}
           ::rpm        {:color (Color/BLUE)      :unit "rpm" :label "Umdrehungen"} 
           ::efficiency {:color (Color. 0x817066) :unit "%" :label "Wirkungsgrad" :min 0 :max 99.999 
                         :color-scale (concat (cjf/fixed-color-scale (cjf/create-color-scale [0 [100 0 0]] [80 [255 0 0]] [90 [255 255 0]]) (range 0 90 10))
                                              (cjf/fixed-color-scale (cjf/create-color-scale [90 [255 255 0]] [101 [0 255 0]]) (range 90 101 0.1)))}}]
    (m2 (m1 val) {:color (Color/BLACK) :unit "???" :label "unbekannte Größe"})))

(defn- get-series-label-solarlog 
  "Unique human readable label."
  [s]
  (let [[_ wr-id] (re-find #".*\.wr\.(\d+)\..*" s )
        type (get-series-type s)]
    (format "%s von %s" (-> s get-series-type unit-properties :label) wr-id)))

(defn- get-series-label-iec61850 [s]
  (format "%s von %s" (-> s get-series-type unit-properties :label) s))

(defn get-series-values 
  "Call appropriate database queries according to (get-series-type series-name). Returns
sequence of value sequences (seq. of maps with keys :timestamp and :value)."
  ([plant-name series-id start-time end-time] (get-series-values plant-name series-id start-time end-time nil))
  ([plant-name series-id start-time end-time width]
    (let [s (as-sql-timestamp start-time) 
          e (as-sql-timestamp end-time)]
      (if width
        (db/rolled-up-values-in-time-range plant-name series-id s e width)
        (db/rolled-up-values-in-time-range plant-name series-id s e)))))

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
      (.setNumberFormatOverride (util/create-si-prefix-formatter "#.##"  (:unit props))))
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
        (let [r (cjf/create-renderer)] 
          (.. chart getPlot (setRenderer i r))
          ;; set colors
          (.setSeriesPaint r 0 (-> n get-series-type unit-properties :color))))
      names))
  (.. chart getLegend (setItemFont (java.awt.Font. "Courier" java.awt.Font/PLAIN 12))) 
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
(defn label->series-name [all-names label]
  (some (fn [[series-name {:keys [name component]}]] 
          (when (= label (str component "/" name)) series-name)) all-names))

;;  Default definition of a page returning any kind of chart data.
;;  Each URL has the form '/series-of/:id/*/:times/file-name'. :id is the plant id, * is a by | separated list of
;;  series names.
;;  The body has access to these parsed variables: [s e all-names names height width]
(defmacro def-chart-page [file-name additional-keys & body]
  `(defpage ~(format "/series-of/:id/*/:times/%s" file-name) {:keys [~'id ~'* ~'times ~'width ~'height ~@additional-keys]}
     (if-let [[~'s ~'e] (parse-times ~'times)]
       (let [~'e (if (re-matches #"\d{8}-\d{8}" ~'times) (+ ~'e util/ONE-DAY) ~'e) 
             ~'all-names  (db/all-series-names-of-plant ~'id)
             ~'names (map #(or (label->series-name ~'all-names %) %)(distinct (re-seq #"[^\|]+" ~'*))) ;; split at any | chars
             ~'* clojure.core/*
             ~'height (s2i ~'height 850)
             ~'width (s2i ~'width 700)]
         ~@body)
       {:status 400
        :body "Wrong dates!"})))

(def-chart-page "chart.png" [] 
  (let [values (pmap #(get-series-values id % s e width) names)]      
    (-> (create-time-series-chart names values)
      (ch/set-x-label "Zeit")
      (ch/set-y-label (-> names first get-series-type unit-properties :label))
      (ch/set-title (str "Chart für den Zeitraum " (.format (util/dateformat) s) " bis " (.format (util/dateformat) e)))
      (enhance-chart names)
        (return-image :height height :width width))))

(defn- day-number [{millis :timestamp}]
  (int (/ millis (* 1000 60 60 24))))

;; show day that represents the biggest discord of a given series.
(def-chart-page "discord.png" [num]
  (let [num (s2i num 1)
        name (first names) 
        data (get-series-values id name s e)  
        days (partition-by day-number data)
        days (remove #(< (count %) 1000) days)
        daily-date (map #(f/resample (map :value %) 100) days)
        _ (println (frequencies (map count daily-date)))
        daily-date (remove #(< (count %) 90) daily-date) 
        discords (discord/find-discords-in-seqs daily-date num)
        discord-days (->> discords
                       (map #(nth days (first %)))
                       (map vec)
                       (map (fn [day] (map #(update-in % [:timestamp] mod util/ONE-DAY) day))))]
    (-> (create-time-series-chart 
          (for [[idx v] discords] (format "Discord am %s: %f" (.format (util/dateformat) (-> days (nth idx) first :timestamp)) v)) 
          discord-days 
          str)
      (ch/set-x-label "Zeit")
      (ch/set-y-label (-> name get-series-type unit-properties :label))
      (ch/set-title (str "Die ungewöhnlichsten Tage"))
      (enhance-chart [name])
      (return-image :height height :width width))))

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
                        (map #(.format (util/dateformat) (:time %)) data) (map #(/ (:value %) 1000) data)
                        ;:title (format "Gesamtertrag für %s WR %s im Zeitraum %s bis %s" id wr-id (.format (util/dateformat) s) (.format (util/dateformat) e))
                        :x-label "Zeit"
                        :y-label "Ertrag in kWh"))] 
      (doto (.. chart getPlot getDomainAxis)
        (.setCategoryLabelPositions org.jfree.chart.axis.CategoryLabelPositions/UP_90))
      (return-image chart :height (s2i height 250) :width (s2i width 400)))
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

(def-chart-page "heat-map.png" [hours minutes]
  (let [name (first names)
        block-time-len (hm (s2i hours 0) (s2i minutes 5))
        values (get-series-values id name s e (/ (- e s) block-time-len))
        ;values (alg/simulate-insolation-values s e false {:latitude 37.606875 :longitude -8.087344}) 
        days (->> values (partition-by day-number) (insert-missing-days s e)) 
        daily-start (hm 0 0)
        daily-end (hm 24 00)
        gridded (map #(align/smooth (align/into-time-grid (map (juxt :timestamp :value) %) daily-start daily-end block-time-len)) days)
        days (vec (map #(vec (map second %)) gridded))
        x-max (count days)
        y-max (apply max (map count days))
        f (fn [x y] 
            (if-let [v (get-in days [(int (/ (- x s) util/ONE-DAY)) (int (/ (- y daily-start) block-time-len))])]
              v
              0))
        props (-> name get-series-type unit-properties) 
        time-axis (doto (org.jfree.chart.axis.DateAxis.) (.setTimeZone (java.util.TimeZone/getTimeZone "UTC"))) 
        chart (doto (cjf/heat-map f s (+ s (clojure.core/* util/ONE-DAY x-max)) daily-start daily-end 
                                  :color? true
                                  :title (format "Tagesverläufe von %s im Zeitraum %s" name times)
                                  :x-label "Tag"
                                  :y-label "Werte"
                                  :z-label (props :label) 
                                  :x-step (count days)
                                  :y-step y-max
                                  :z-min (props :min)
                                  :z-max (props :max)
                                  :colors (props :color-scale)
                                  :color? true)
                (.. getPlot getRenderer (setBlockWidth util/ONE-DAY))
                (.. getPlot getRenderer (setBlockHeight block-time-len)))] 
    (.. chart getPlot (setDomainAxis (org.jfree.chart.axis.DateAxis.)))
    (.. chart getPlot (setRangeAxis time-axis))
    (return-image chart :height height :width width)))

(def-chart-page "relative-heat-map.png" [] ; TODO configurable ranges, configurable color scales
  (let [[name2 name1] names
        [power wind-speed] [(map :value (get-series-values id name1 s e)) 
                            (map :value (get-series-values id name2 s e))] 
        [min-p max-p] (apply (juxt min max) power)
        [min-w max-w] (apply (juxt min max) wind-speed)
        ;      [min-p max-p] [0 1000000]
        ;      [min-w max-w] [0 15] 
        x-steps 160
        y-steps 200
        x-bin-width (/ (- max-w min-w) x-steps)
        y-bin-width (/ (- max-p min-p) y-steps)
        bin (fn [min max step] 
              (let [steps (partition 2 1 (range min max step))] 
                (fn [v] (org.clojars.smee.seq/find-where (fn [[a b]] (<= a v b)) steps))))
        bin-w (bin min-w max-w x-bin-width)
        bin-p (bin min-p max-p y-bin-width)
        bins (->> (map vector wind-speed power)
               (sort-by first)
               (partition-by #(int (/ (first %) x-bin-width)))
               (reduce #(assoc % (bin-w (ffirst %2)) %2) {}) 
               (org.clojars.smee.map/map-values 
                 (fn [bin] (->> bin 
                             (sort-by second)
                             (partition-by #(int (/ (second %) y-bin-width)))
                             (reduce #(assoc % (bin-p (second (first %2))) %2) {})
                             (org.clojars.smee.map/map-values count))))
               time)
        f (fn [x y] ;(println [x y]) 
            (if-let [v (get-in bins [(bin-w x) (bin-p y)])] v 0))
        color-scale (cjf/create-color-scale [0 [0 0 0]] 
                                            [1 [0 255 0]]
                                            [10 [0 255 255]]
                                            [1000 [0 0 255]] 
                                            [3000 [255 0 0]])
        fixed-color-scale (cjf/fixed-color-scale color-scale (range 0 1000 5))
        fixed-color-scale (cjf/fixed-color-scale color-scale [0 1 5 10 50 100 1000 2000 10000])
        chart (doto (chart-utils.jfreechart/heat-map f min-w max-w min-p max-p :x-step x-steps :y-step y-steps
                                                     :title (str name1 " vs. " name2) 
                                                     :colors fixed-color-scale
                                                     :x-label name1
                                                     :y-label name2
                                                     :z-label "Häufigkeit" 
                                                     )
                (.. getPlot getRenderer (setBlockHeight (/ (- max-p min-p) y-steps)))
                (.. getPlot getRenderer (setBlockWidth (/ (- max-w min-w) x-steps))))]
    (return-image chart :height height :width width))) 



;;;;;;;;;;;;;;;;;;;;;;;;;;;;; render a correlation matrix plot ;;;;;;;;;;;;;;;;
(def-chart-page "correlation.png" [] 
  (let [values (for [name names] (map :value (get-series-values id name s (+ s util/ONE-DAY)))) 
        img (-> values 
              tc/calculate-correlation-matrix 
              (tc/render-frame names (.format (util/dateformat) s)))]
    (return-image img)))

;;;;;;;;;;;;;;;;;;;;;;;;;;; render change points in a chart
(def-chart-page "changepoints.png" [rank negative zero confidence max-level maintainance] 
  (let [name (first names)
        ranks? (Boolean/parseBoolean rank)
        negative? (Boolean/parseBoolean negative) 
        zeroes? (Boolean/parseBoolean zero)
        maintainance? (Boolean/parseBoolean maintainance)
        maintainance-dates (db/maintainance-intervals id)
        maintainance-date? (fn [t] (some #(and (>= t (:start %)) (<= t (:end %))) maintainance-dates))
        confidence (s2d confidence 0.999999) 
        max-level (s2i max-level 2)
        insol-name (cond (re-matches #"INVU1.*" name) "INVU1/MMET1.HorInsol.mag.f" 
                     (re-matches #"INVU2.*" name) "INVU2/MMET1.HorInsol.mag.f"
                     :else (second names))
        values (db/db-max-current-per-insolation id name insol-name s e) ;todo make configurable
        hours (sort (distinct (map :hour values)))
        charts (for [hour hours] 
                 (let [values (filter #(= hour (:hour %)) values)
                       values (if zeroes? (remove #(or (nil? (:value %)) (zero? (:value %))) values) values) ;remove all zeroes  
                       values (if maintainance? (remove #(maintainance-date? (:timestamp %)) values) values) 
                       times (map (comp as-unix-timestamp :timestamp) values)
                       values (map (comp #(or % 0) :value) values)
                       values (if ranks? (f/rankify values) values)
                       values (mapv double values) 
                       t-map (apply hash-map (flatten (map-indexed vector times)))
                       chart (ch/time-series-plot times values)
                       cps (cp/rec-change-point values :min-confidence confidence :max-level max-level :bootstrap-size 1000)
                       cps (if negative? (filter (comp neg? :mean-change) cps) cps)
                       ]
                   (ch/set-y-label chart (str hour " Uhr")) 
                   (cjf/set-discontinuous chart)
                   (ch/add-lines chart times (f/ema 0.05 values))
                   (when (not-empty cps) 
                     (doseq [{:keys [changepoint level confidence mean-change]} cps
                             :let [label (str "level " level "\n,m-c= " mean-change)
                                   label (format "%.1f%% (lvl. %d)" (* 100.0 mean-change) level)]]
                       (cjf/add-domain-marker chart (get t-map changepoint) label)))
                   chart))] 
    (return-image (doto (->> charts
                          (map (memfn getPlot))
                          (apply cjf/combined-domain-plot)
                          (org.jfree.chart.JFreeChart.))
                    (ch/set-title (format "Signifikante Veränderungen im Verlauf von %s\n(%s)" (get-in (db/all-series-names-of-plant id) [name :name]) name))
                    (ch/add-subtitle (str (.format (util/dateformat) s) " - " (.format (util/dateformat) e)))
                    (ch/set-x-label "Datum") 
                    (.removeLegend)
                    ;(enhance-chart names)
                    )
                  :height height 
                  :width width)))


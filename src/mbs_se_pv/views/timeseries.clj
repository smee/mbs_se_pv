(ns mbs-se-pv.views.timeseries
    (:require 
      [clojure.string :as string]
      [mbs-se-pv.views 
       [common :as common]
       [charts :as ch]
       [reports :as report]]
      [mbs-db.core :as db])
    (:use noir.core
          hiccup.core
          hiccup.page-helpers
          hiccup.form-helpers
          mbs-se-pv.views.util
          [org.clojars.smee 
           [map :only (map-values)]
           [util :only (s2i)]]))

(declare toolbar-links)

;;;;;;;;;;;;;;;;;;;;;;;; show metadata as table ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:private metadata-label
  {:anlagenkwp "Installierte Leistung in Wp"
   :hpausricht "Ausrichtung der Module"
   :verguetung "Vergütung pro kWh"
   :hpemail  "Kontaktemail"
   :hpmodul "PV-Modul" 
   :hpstandort "Standort"
   :hpwr "Wechselrichter"
   :hpinbetrieb "Inbetriebnahme"
   :hpleistung "Leistung"
   :hpbetreiber "Betreiber"
   :serialnr "Seriennummer des Datenloggers"
   :hppostleitzahl "Postleitzahl"
   :id "Name"
   :anzahlwr "Anzahl installierter Wechselrichter"})
(defn ^:private metadata-value [k v]
  (case k
    :anlagenkwp (.format (create-si-prefix-formatter "###.##" "Wh") v)
    :verguetung (format "%.2f€" (float (/ v 100)))
    v))

(defn- metadata-table [metadata]
  (let [wr-details (:wr metadata)
        metadata (dissoc metadata :wr)
        k (sort (keys metadata))]
    [:table.condensed-table.zebra-striped 
     (for [[k v] (into (sorted-map) metadata) 
           :let [label (metadata-label k)] 
           :when label]
       [:tr [:th label] [:td (metadata-value k v)]])]))

(defpage metadata-page "/details/:id" {name :id}
  (let [metadata (-> name db/get-metadata first second)
        now (System/currentTimeMillis)
        today (.format (dateformatrev) now)
        last-month (.format (dateformatrev) (- now (* 365 ONE-DAY)))
        w 400
        h 250]
    (common/layout-with-links 
      (toolbar-links name 1)
      nil
      [:div.row
       [:div.span6
        [:h3 "Anlagendaten"]
        (metadata-table metadata)
        [:a.btn.large.success {:href (resolve-uri (url-for all-series {:id name}))} "Messwerte"]]
       [:div.span6.offset1
        [:h3 "Erträge im letzten Jahr"]
        [:h4 "Gesamtertrag pro Tag"]
        [:img.loading-bg {:src (resolve-uri (format "/gains/%s/%s-%s/chart.png?unit=day&width=%d&height=%d" name last-month today w h))
                          :width w
                          :height h}]
        [:h4 "Gesamtertrag pro Woche"]
        [:img.loading-bg {:src (resolve-uri (format "/gains/%s/%s-%s/chart.png?unit=week&width=%d&height=%d" name last-month today w h))
                          :width w
                          :height h}]
        [:h4 "Gesamtertrag pro Monat"]
        [:img.loading-bg {:src (resolve-uri (format "/gains/%s/%s-%s/chart.png?unit=month&width=%d&height=%d" name last-month today w h))
                          :width w
                          :height h}]]])))

;;;;;;;;;;;;;; show all available time series info per pv installation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- label-for-type [s]
  (case s
    "pdc" "Leistung DC"
    "pac" "Leistung AC"
    "temp" "Temperatur"
    "udc" "Spannung DC"
    "efficiency" "Wirkungsgrad"
    "gain" "Tagesertragsverlauf"
    "daily-gain" "Ertrag pro Tag"
     s))

(defn- nice-labels [[p1 p2 p3]]
  (->> 
    (list (list "Wechselrichter" [:sub p1]) 
          (if p3 (list "String" [:sub p2]) (label-for-type p2))
          (label-for-type p3))
    (keep identity)))

(defn fix-parts-order [[p1 p2 p3 :as l]]
  (if (nil? p3)
     l
    (list p1 p3 p2)))

(defn- split-series-name [n]
  (->> #"\."
    (string/split n)
    next
    (remove #{"wr" "string"})
    fix-parts-order))

(defn- restore-wr-hierarchy [names]
  (->> names
    (map #(concat ["Daten" "nach Bauteil"] (nice-labels (split-series-name %)) [%]))
    restore-hierarchy))

(defn- cluster-by-type [names]
  (->> names
    (map #(let [parts (-> % split-series-name nice-labels)] 
            (concat ["nach Datentyp"] (vector (last parts)) (butlast parts) [%])))
    restore-hierarchy))

(defn- make-nested-list [nested]
  [:ul 
   (for [[k vs] nested]
     (if (and (sequential? vs) (= 1 (count vs)))
       [:li {:data (format "{series: '%s'}" (first vs))} k]
       [:li {:class "folder"} k (make-nested-list vs)]))])

(defpartial series-tree [id names elem-id]
  (let [efficiency-names (distinct (map #(str id ".wr." (extract-wr-id %) ".efficiency") names))
        daily-gain-names (distinct (map #(str id ".wr." (extract-wr-id %) ".daily-gain") names))
        tree (->> names
               (concat efficiency-names daily-gain-names)
               ((juxt restore-wr-hierarchy cluster-by-type))
               (apply concat)
               make-nested-list)]
     [:div {:id elem-id} 
      tree
      ;; render tree via jquery plugin
      (javascript-tag 
        (format
          "$('#%s').dynatree({
           checkbox:true,
           selectMode: 3,          
           persist: false,
           minExpandLevel: 2});"
          elem-id))]))

(defpage all-series "/series-of/:id" {id :id}
  (let [q (str id ".%")
        c (db/count-all-series-of q)
        names (db/all-series-names-of q)
        {:keys [min max]} (db/min-max-time-of (first names))
        date (.format (dateformat) max)
        base-url (or hiccup.core/*base-url* "")]
        
    (common/layout-with-links
      (toolbar-links id 2)
      ;; sidebar
      [:form.form-stacked 
        [:h5 "Datum"]
        (text-field {:placeholder "Startdatum" :class "span2"} "start-date" date)
        (text-field {:placeholder "Enddatum" :class "span2"} "end-date" date)
        [:h5 "Datenreihen"]
        (series-tree id names "series-tree")
        [:div
         [:h5 "Art der Anzeige:"]
         [:ul.inputs-list
          [:li [:label (radio-button "chart-type" true "chart") "Zeitreihe"]]
          [:li [:label (radio-button "chart-type" false "heat-map") "Heatmap"]]
          [:li [:label (radio-button "chart-type" false "discord") "Ungewöhnlicher Tag"]]]]
        [:div 
         [:h5 "Größe:"]
         [:input#chart-width.span2 {:value "850" :type "number"}] 
         [:span "X"] 
         [:input#chart-height.span2 {:value "700" :type "number"}]]
        [:a.btn.primary {:href "" :onclick (render-javascript-template "templates/load-chart.js" base-url id)} "Anzeigen"]
        [:a.btn {:href "#" :onclick (render-javascript-template "templates/show-report.js" base-url id)} "Report Wirkungsgrad"]]       
      ;; main content
      [:div.row 
       [:div.span12        
        [:h2 "Chart"]
        [:div#current-chart "Bitte wählen Sie links die zu visualisierenden Daten und ein Zeitinterval aus."
         [:img#chart-image {:src ""}]]]]
      ;; render calendar input via jquery plugin
      (javascript-tag (render-javascript-template "templates/date-selector.js" "#start-date" date min max))
      (javascript-tag (render-javascript-template "templates/date-selector.js" "#end-date" date min max)))))

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id id}) "Allgemeines")
   (link-to (url-for all-series {:id id}) "Messwerte")]
  )


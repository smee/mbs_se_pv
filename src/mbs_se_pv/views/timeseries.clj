(ns mbs-se-pv.views.timeseries
    (:require 
      [clojure.string :as string]
      [mbs-se-pv.views 
       [common :as common]
       [charts :as ch]
       [util :as util]
       [psm-names :as names]]
      [mbs-db.core :as db])
    (:use [noir 
           core
           [options :only (resolve-url)]]
          [hiccup core element form]
          [ring.util.codec :only (url-encode)]
          [org.clojars.smee 
           [map :only (map-values)]
           [util :only (s2i)]]))

(declare toolbar-links)

;;;;;;;;;;;;;;;;;;;;;;;; show metadata as table ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:private installation-labels
  [:first-date "Erster Messwert am"
   :last-date "Letzter Messwert am"
   :id "Name der Anlage"
   :anlagenkwp "Installierte Leistung in Wp"
   :anzahlwr "Anzahl installierter Wechselrichter"
   :hpmodul "PV-Module" 
   :hpwr "Wechselrichter"
   :hpausricht "Ausrichtung der Module"
   ])
(def ^:private personal-labels
  [:hpbetreiber "Betreiber"
   :verguetung "Vergütung pro kWh"
   :hpstandort "Standort"
   :hppostleitzahl "Postleitzahl"
   :hpemail  "Kontaktemail"
   :hpinbetrieb "Inbetriebnahme"
   :serialnr "Seriennummer des Datenloggers"
   ])

(defn ^:private metadata-value [k v]
  (case k
    :anlagenkwp (.format (util/create-si-prefix-formatter "###.##" " Wh") v)
    :verguetung (format "%.2f €" (float (/ v 100)))
    v))

(defn- metadata-table [metadata labels]
  (let [wr-details (:wr metadata)
        metadata (dissoc metadata :wr)
        k (sort (keys metadata))]
    [:table.table.table-striped.table-condensed 
     (for [[k label] (partition 2 labels) 
           :let [value (metadata k)] 
           :when value]
       [:tr [:th.span4 label] [:td (metadata-value k value)]])]))


(defpartial render-gain-image [name last-month today w h type]
  [:div.loading-bg{:style (format "width:%dpx;height:%dpx;" w h)}
   [:img {:src (resolve-url (format "/gains/%s/%s-%s/chart.png?unit=%s&width=%d&height=%d" name last-month today type w h))
          :width w
          :height h}]])

(defpage metadata-page "/details/:id" {plant :id}
  (let [{:keys [min max]} (db/min-max-time-of plant (-> plant db/all-series-names-of-plant ffirst))
        metadata (-> plant db/get-metadata first second (assoc :first-date (.format (util/dateformat) min)) (assoc :last-date (.format (util/dateformat) max)))
        now (System/currentTimeMillis)
        today (.format (util/dateformatrev) now)
        last-month (.format (util/dateformatrev) (- now (* 365 util/ONE-DAY)))
        w 400
        h 250]
    (common/layout-with-links 
      (toolbar-links plant 1)
      nil
      [:div.span6
        [:h3 "Anlagendaten"]
        (metadata-table metadata installation-labels)
        [:h3 "Betreiber"]
        (metadata-table metadata personal-labels)
        [:a.btn.btn-large.btn-success {:href (resolve-url (url-for all-series {:id plant}))} "Messwerte"]]
       [:div.span6
        [:h3 "Erträge im letzten Jahr"]
        [:h4 "Gesamtertrag pro Tag"]
        (render-gain-image plant last-month today w h "day")
        [:h4 "Gesamtertrag pro Woche"]
        (render-gain-image plant last-month today w h "week")
        [:h4 "Gesamtertrag pro Monat"]
        (render-gain-image plant last-month today w h "month")])))

;;;;;;;;;;;;;; show all available time series info per pv installation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- extract-ln-name [name]
  (let [rev (reverse name)
        [digits-rev rev] (split-with #(Character/isDigit %) rev)
        [ln-name-rev prefix-rev] (split-at 4 rev)]
    (map #(apply str (reverse %)) [prefix-rev ln-name-rev digits-rev])))

(defn- split-iec-name [n]
  (let [[ld-name ln-name & _] (string/split n #"/|\.")
        [prefix ln-name id] (extract-ln-name ln-name)]
    (remove empty? [ld-name ln-name prefix id])))

(defn- restore-physical-hierarchy [names]
  (let [iec (keys names)
        splitted (map split-iec-name iec)
        labels-and-names (map #(vector (get names %) %) iec)] 
    (util/restore-hierarchy (map #(into (vec %1) %2) splitted labels-and-names))))

(defn- split-iec-name-typed [n]
  (let [[ld-name ln-name type & _] (string/split n #"/|\.")
        [prefix ln-name id] (extract-ln-name ln-name)]
    (remove empty? [(names/type-names type type) ld-name ln-name prefix id])))

(defn- cluster-by-type [names]
  (let [iec (keys names)
        splitted (map split-iec-name-typed iec)
        labels-and-names (map #(vector (get names %) %) iec)] 
    (util/restore-hierarchy (map #(into (vec %1) %2) splitted labels-and-names))))

(defn- make-nested-list [nested]
  [:ul 
   (for [[k vs] nested]
     (if (and (sequential? vs) (= 1 (count vs)))
       [:li {:data (format "{series: '%s'}" (first vs))} k]
       [:li {:class "folder"} k (make-nested-list vs)]))])

(defpartial series-tree [id names elem-id]
  (def n names) 
  (let [by-phys {"nach Bauteil" (restore-physical-hierarchy names)}
        by-type {"nach Datenart" (cluster-by-type names)}
        tree (->> by-phys
               (merge by-type)               
               (clojure.walk/postwalk #(if (map? %) (into (sorted-map) %) %))
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
  (let [c (db/count-all-series-of-plant id)
        names (db/all-series-names-of-plant id)
        {:keys [min max]} (db/min-max-time-of id (ffirst names))
        date (.format (util/dateformat) max)
        base-url (util/base-url)]

    (common/layout-with-links
      (toolbar-links id 2)
      ;; sidebar
      [:div.span3
       [:form.form-vertical.well 
        [:div
         [:h4 "Datum"]
         [:div.input-prepend 
          [:span.add-on "von: "] 
          (text-field {:placeholder "Startdatum" :class "input-small"} "start-date" date)]
         [:div.input-prepend
          [:span.add-on "bis: "]
          (text-field {:placeholder "Enddatum" :class "input-small"} "end-date" date)]]
        [:div
         [:h4 "Datenreihen"]
         (series-tree id names "series-tree")]
        [:div
         [:h4 "Art der Anzeige:"]
         [:div.controls
          [:label.radio (radio-button "chart-type" false "chart") "Zeitreihe"]
          [:label.radio (radio-button "chart-type" false "heat-map") "Heatmap"]
          [:label.radio (radio-button "chart-type" false "discord") "Ungewöhnlicher Tag"]
          [:label.radio (radio-button "chart-type" true "interactive-client") "Interaktive Ansicht"]
          [:label.radio (radio-button "chart-type" false "interactive-map") "Interaktiver Zoom"]]
         ]
        [:div
         [:h4 "Größe:"]
         [:input#chart-width.input-mini {:value "850" :type "number"}] 
         [:span "X"] 
         [:input#chart-height.input-mini {:value "700" :type "number"}]
         [:span "px"]]
        [:button.btn-primary.btn-large {:href "" :onclick (util/render-javascript-template "templates/load-chart.js" base-url id)} 
         [:i.icon-picture.icon-white]
         " Anzeigen"]
        ]
      [:form.form-vertical 
       #_[:div.well
         [:h4 "Report Wirkungsgrad"]
         [:span.help-inline "Bitte wählen Sie den Monat aus, für den ein Report erstellt werden soll:"]
         (text-field {:placeholder "Monat für Report" :class "input-small"} "report-date" date)
         [:button.btn {:href "#" :onclick (util/render-javascript-template "templates/show-report.js" base-url id)} 
          [:i.icon-list-alt]
          " Erstellen"]]]]
      ;; main content
      [:div.span9        
       [:h2 "Chart"]
       [:div#current-chart "Bitte wählen Sie links die zu visualisierenden Daten und ein Zeitinterval aus."
        ]]
      ;; render calendar input via jquery plugin
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#start-date" date min max))
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#end-date" date min max))
      #_(javascript-tag (util/render-javascript-template "templates/date-selector.js" "#report-date" date min max))
      ;;render interactive "maps"
      (hiccup.page/include-css "http://cdn.leafletjs.com/leaflet-0.4/leaflet.css")
      (hiccup.page/include-js "http://cdn.leafletjs.com/leaflet-0.4/leaflet.js")
      ;;render interactive client side charts
      (hiccup.page/include-css "/css/chart/rickshaw.min.css")
      (hiccup.page/include-js "/js/chart/d3.min.js")
      (hiccup.page/include-js "/js/chart/d3.layout.min.js")
      (hiccup.page/include-js "/js/chart/rickshaw.min.js"))))

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id (url-encode id)}) "Allgemeines")
   (link-to (url-for all-series {:id (url-encode id)}) "Messwerte")]
  )


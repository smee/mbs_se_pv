(ns mbs-se-pv.views.timeseries
    (:require 
      [clojure 
       [string :as string]]
      [mbs-se-pv.views 
       [common :as common]
       [charts :as ch]
       [util :as util]
       [psm-names :as names]
       [calendar :as cal]]
      [mbs-db.core :as db])
    (:use [noir 
           core
           [options :only (resolve-url)]]
          [hiccup core element form]
          [ring.util.codec :only (url-encode)]
          [org.clojars.smee 
           [time :only (as-date)] 
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
    :anlagenkwp (.format (util/create-si-prefix-formatter "###.##" "Wh") v)
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


(defpartial render-gain-image [name time-ago today w h type]
  [:div.loading-bg{:style (format "width:%dpx;height:%dpx;" w h)}
   [:img {:src (resolve-url (format "/gains/%s/%s-%s/chart.png?unit=%s&width=%d&height=%d" name time-ago today type w h))
          :width w
          :height h}]])

(defpage metadata-page "/details/:id" {plant :id}
  (let [{:keys [min max]} (db/min-max-time-of plant (-> plant db/all-series-names-of-plant ffirst))
        metadata (-> plant db/get-metadata first second (assoc :first-date (.format (util/dateformat) min)) (assoc :last-date (.format (util/dateformat) max)))
        now (System/currentTimeMillis)
        today (.format (util/dateformatrev) now)
        one-year-ago (.format (util/dateformatrev) (- now (* 365 util/ONE-DAY)))
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
        [:a.btn.btn-large.btn-success {:href (resolve-url (url-for all-series {:id plant}))} "Messwerte"]
        [:h3 "Vorhandene Daten pro Tag"]
        [:div#calendar.span9
         [:div.controls
          (drop-down "calendar-data" 
                     [["Fehlende Daten" (resolve-url (url-for cal/missing-data {:id (url-encode plant)}))]
                      ["Wartungsarbeiten" (resolve-url (url-for cal/maintainance-dates {:id (url-encode plant)}))]
                      ])
          (drop-down "color-scale" 
                     [["Rot-Gelb-Grün" "RdYlGn"]
                      ["Grün-Gelb-Rot" "GnYlRd"]
                      ["Gelb-Orange-Braun" "YlOrBr"] 
                      ["Blau" "Blues"]
                      ["Grün" "Greens"]
                      ["Orange" "Oranges"]
                      ["Grau" "Greys"]
                      ["???" "Spectral"]])]]]
       [:div.span6
        [:h3 "Erträge im letzten Jahr"]
        [:h4 "Gesamtertrag pro Tag"]
        (render-gain-image plant one-year-ago today w h "day")
        [:h4 "Gesamtertrag pro Woche"]
        (render-gain-image plant one-year-ago today w h "week")
        [:h4 "Gesamtertrag pro Monat"]
        (render-gain-image plant one-year-ago today w h "month")]
       (javascript-tag (util/render-javascript-template 
                         "templates/calendar.js"
                         (util/base-url)
                         "#calendar" 
                         "select#calendar-data"                         
                         "select#color-scale"                         
                         (resolve-url (url-for all-series {:id (url-encode plant)})))))))

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
        labels-and-names (map #(vector (get-in names [% :name]) %) iec)] 
    (util/restore-hierarchy (map #(into (vec %1) %2) splitted labels-and-names))))

(defn- phys-type-of [name]
  (let [[_ _ type] (string/split name #"/|\.")]
    (names/type-names type type)))

(defn- split-iec-name-typed [n]
  (let [[ld-name ln-name type & _] (string/split n #"/|\.")
        [prefix ln-name id] (extract-ln-name ln-name)]
    (remove empty? [(names/type-names type type) ld-name prefix id])))

(defn- cluster-by-type [names]
  (let [iec (keys names)
        splitted (map split-iec-name-typed iec)
        labels-and-names (map #(vector (get-in names [% :name]) %) iec)] 
    (util/restore-hierarchy (map #(into (vec %1) %2) splitted labels-and-names))))

(defn- make-nested-list [nested]
  [:ul {:style "display:none;"} ; will be replaced by the jquery tree plugin 
   (for [[k vs] nested]
     (if (and (sequential? vs) (= 1 (count vs)))
       [:li {:data (format "{series: '%s'}" (first vs))} k]
       (do (def x nested) 
       [:li {:class "folder"} k (make-nested-list vs)])))])

(defpartial series-tree [id names elem-id]
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
          "$(document).ready(function() { 
           $('#%s').dynatree({
           checkbox:true,
           selectMode: 3,          
           persist: false,
           minExpandLevel: 1})});"
          elem-id))]))

(defpage all-series "/series-of/:id" {:keys [id selected-date]}
  (let [c (db/count-all-series-of-plant id)
        names (db/all-series-names-of-plant id)
        {:keys [min max]} (db/min-max-time-of id (ffirst names))
        selected-date (when selected-date (as-date (.parse (util/dateformatrev) selected-date))) 
        date (.format (util/dateformat) (or selected-date max))
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
          (text-field {:placeholder "Enddatum" :class "input-small"} "end-date" date)]
         [:div
          [:a.btn.btn-mini {:href "#" :onclick "shiftTime(-1,0,0)"} "< Tag"]
          [:a.btn.btn-mini {:href "#" :onclick "shiftTime(0,-1,0)"} "< Monat"]
          [:a.btn.btn-mini {:href "#" :onclick "shiftTime(0,0,-1)"} "< Jahr"]]
         [:div
          [:a.btn.btn-mini {:href "#" :onclick "shiftTime(1,0,0)"} "Tag >"]
          [:a.btn.btn-mini {:href "#" :onclick "shiftTime(0,1,0)"} "Monat >"]
          [:a.btn.btn-mini {:href "#" :onclick "shiftTime(0,0,1)"} "Jahr >"]]
         [:label.checkbox (check-box "rerender" true) "automatisch neu zeichnen"]]
        [:div
         [:h4 "Datenreihen"]
         (series-tree id names "series-tree")]
        [:div
         [:h4 "Art der Anzeige:"]
         [:div.controls
          (drop-down "chart-type" [["Interaktive Ansicht" "interactive-client"]
                                   ["Statische Ansicht" "chart"]
                                   ["Heatmap" "heat-map"]
                                   ["Ungewöhnlicher Tag" "discord"]
                                   ["Verhaltensänderung" "changepoints"]
                                   ["Interaktiver Zoom" "interactive-map"]
                                   ["Korrelationen" "correlation"]]
                     "interactive-client")]]
        [:div#changepoint-parameter
         [:h4 "Weitere Parameter"]
         [:div.controls
          [:label.checkbox (check-box :rank false "rank") "Rang statt Rohwerten verwenden"]
          [:label.checkbox (check-box :zero false "zero") "Nullwerte löschen"]
          [:label.checkbox (check-box :negative false "neg") "Nur Verschlechterungen anzeigen"]]
         [:div.input-prepend
          [:span.add-on "CI: "]
          (text-field {:placeholder "p-Wert" :class "input-small"} "confidence" 0.9999)]
         [:div.input-prepend
          [:span.add-on "lvl: "]
          (text-field {:placeholder "Max. level" :class "input-small"} "max-level" 2)]] 
        [:div
         [:h4 "Größe:"]
         [:input#chart-width.input-mini {:value "850" :type "number"}] 
         [:span "X"] 
         [:input#chart-height.input-mini {:value "700" :type "number"}]
         [:span "px"]]
        [:button#render-chart.btn-primary.btn-large 
         [:i.icon-picture.icon-white]
         " Anzeigen"]
        ]]
      ;; main content
      [:div.span9        
       [:h2 "Chart"]
       [:div#current-chart "Bitte wählen Sie links die zu visualisierenden Daten und ein Zeitinterval aus."]]
      ;; render calendar input via jquery plugin
      (hiccup.page/include-js "https://ajax.googleapis.com/ajax/libs/jqueryui/1.8.16/jquery-ui.min.js" 
                              "/js/jquery.dynatree.min.js" 
                              "/js/datepicker.js")
      (hiccup.page/include-css "/css/dynatree/ui.dynatree.css" "/css/datepicker.css") 
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#start-date" date min max))
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#end-date" date min max))
      (javascript-tag (util/render-javascript-template "templates/load-chart.js" "#render-chart" base-url id))
      (javascript-tag "$('#chart-type').change(function(){
                            var params=$('#changepoint-parameter'); 
                            if('changepoints'==$(this).val()){ params.slideDown();} else{ params.slideUp();}})"))))

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id (url-encode id)}) "Allgemeines")
   (link-to (url-for all-series {:id (url-encode id)}) "Messwerte")]
  )



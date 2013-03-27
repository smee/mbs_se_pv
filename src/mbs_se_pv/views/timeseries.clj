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
           [options :only (resolve-url)]
           [response :only [json]]]
          [cheshire.core :as json] 
          [hiccup core element form page]
          [ring.util.codec :only (url-encode)]
          [org.clojars.smee 
           [time :only (as-date)] 
           [util :only (s2i)]
           [map :only (map-values)]]
          [clojure.walk :only [postwalk]]))

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
       (hiccup.page/include-css "/css/colorbrewer.css")
       (hiccup.page/include-js "/js/chart/d3.v2.min.js") 
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
    (remove empty? [ld-name ln-name id prefix])))

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
    (remove empty? [(names/type-names type type) ld-name id prefix])))

(defn- cluster-by-type [names]
  (let [iec (keys names)
        splitted (map split-iec-name-typed iec)
        labels-and-names (map #(vector (get-in names [% :name]) %) iec)] 
    (util/restore-hierarchy (map #(into (vec %1) %2) splitted labels-and-names))))

(defn- make-nested-list [selected? nested]
  [:ul {:style "display:none;"} ; will be replaced by the jquery tree plugin 
   (for [[k vs] nested]
     (if (and (sequential? vs) (= 1 (count vs)))
       [:li {:data (format "{series: '%s', select: %b}" (first vs) (selected? (first vs)))} k]
       [:li.folder k (make-nested-list selected? vs)]))])

(defpartial series-tree [id names elem-id selected?]
  (let [;not-all-caps (partial every? #(Character/isUpperCase %))
        by-phys {"nach Bauteil" (restore-physical-hierarchy names)}
        by-type {"nach Datenart" (cluster-by-type names)} 
        tree (->> by-phys
               (merge by-type)               
               (postwalk #(if (map? %) (into (sorted-map) %) %))
               (postwalk #(if (map? %) (reduce (fn [m[k v]] 
                                                 (if (and (map? v) (= 1 (count (keys v))) #_(not-all-caps k))
                                                   (apply assoc (dissoc m k) (first v))
                                                   m)) % (seq %)) %)) 
               (make-nested-list selected?))]
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

(def ^:private default-series-settings
  {:visType "dygraph.json"
   :maintenance true
   :zero false
   :rank false
   :negative false
   :confidence 0.9999
   :maxLevel 2
   :minHist 0.05
   :maxHist 2
   :bins 500
   :days 30
   :skipMissing true
   :sensor ""
   :width 950
   :height 600})

(defn- render-series-page [id {:keys [startDate endDate] :as params}]
  (let [c (db/count-all-series-of-plant id)
        names (db/all-series-names-of-plant id)
        {:keys [min max]} (db/min-max-time-of id (ffirst names))
        startDate (or startDate (.format (util/dateformat) max)) 
        endDate (or endDate (.format (util/dateformat) max)) 
        base-url (util/base-url)]

    (common/layout-with-links
      (toolbar-links id 3)
      ;; sidebar
      [:div.span3
       [:form.form-vertical.well 
        [:div
         [:h4 "Datum"]
         [:div.input-prepend 
          [:span.add-on "von: "] 
          (text-field {:placeholder "Startdatum" :class "input-small"} "startDate" startDate)]
         [:div.input-prepend
          [:span.add-on "bis: "]
          (text-field {:placeholder "Enddatum" :class "input-small"} "endDate" endDate)]
         [:div
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(-1,0,0,0)"} "< Tag"]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(-7,0,0)"} "< Woche"]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,-1,0)"} "< Monat"]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,0,-1)"} "< Jahr"]]
         [:div
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(1,0,0,0)"} "Tag >"]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(7,0,0)"} "Woche >"]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,1,0)"} "Monat >"]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,0,1)"} "Jahr >"]]
         [:label.checkbox (check-box "rerender" true) "automatisch neu zeichnen"]]
        [:div
         [:h4 "Datenreihen"]
         (series-tree id names "series-tree" (set (params :selectedSeries)))]
        [:div
         [:h4 "Art der Anzeige:"]
         [:div.controls
          (drop-down "visType" [["Interaktive Ansicht", "dygraph.json"] 
                                   ["Statische Ansicht" "chart.png"]
                                   ["Heatmap" "heat-map.png"]
                                   ["Verhältnis" "dygraph-ratios.json"] 
                                   ["Entropieänderung" "entropy.json"]
                                   ["Verhaltensänderung" "changepoints.png"]
                                   ["Ungewöhnlicher Tag" "discord.png"]
                                   ["Korrelationen" "correlation.png"]]
                     (params :visType))]]
        [:div#changepoint-parameter {:style (str "display:" (if (= (params :visType) "changepoints.png") "block" "none"))}
         [:h4 "Weitere Parameter"]
         [:div.controls
          [:label.checkbox (check-box :rank (params :rank)) "Rang statt Rohwerten verwenden"]
          [:label.checkbox (check-box :zero (params :zero)) "Nullwerte löschen"]
          [:label.checkbox (check-box :negative (params :negative)) "Nur Verschlechterungen anzeigen"]
          [:label.checkbox (check-box :maintenance (params :maintenance)) "Wartungstage ignorieren"]]
         [:div.input-prepend
          [:span.add-on "CI: "]
          (text-field {:placeholder "p-Wert" :class "input-small"} "confidence" (params :confidence))]
         [:div.input-prepend
          [:span.add-on "lvl: "]
          (text-field {:placeholder "Max. level" :class "input-small"} "maxLevel" (params :maxLevel))]]
        [:div#entropy-parameter {:style (str "display:" (if (= (params :visType) "entropy.json") "block" "none"))} 
         [:h4 "Weitere Parameter"]
         [:div.input-prepend
          [:span.add-on "min: "]
          (text-field {:class "input-small"} "minHist" (params :minHist))]
         [:div.input-prepend
          [:span.add-on "max: "]
          (text-field {:class "input-small"} "maxHist" (params :maxHist))]
         [:div.input-prepend
          [:span.add-on "bins: "]
          (text-field {:class "input-small"} "bins" (params :bins))]
         [:div.input-prepend
          [:span.add-on "Tage: "]
          (text-field {:class "input-small"} "days" (params :days))]
         [:div.controls
          [:label.checkbox (check-box :skipMissing (params :skipMissing)) "Ignoriere lückenhafte Tage"]]         
         [:div.input-prepend
          [:span.add-on "Sensor: "]
          (text-field {:class "input-large" :placeholder "Welche Messreihe?"} "sensor" (params :sensor))]] 
        [:div
         [:h4 "Größe:"]
         (text-field {:type "number" :class "input-mini"} "width" (params :width))
         [:span "X"]
         (text-field {:type "number" :class "input-mini"} "height" (params :height))
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
      (hiccup.page/include-js "/js/jquery-ui.min.js" 
                              "/js/jquery.dynatree.min.js" 
                              "/js/datepicker.js"
                              "/js/chart/dygraph-combined-dev.js"
                              "/js/chart/dygraph-functions.js"
                              "/js/hogan-2.0.0.js"
                              "/js/typeahead.js")
      (hiccup.page/include-css "/css/dynatree/ui.dynatree.css" "/css/datepicker.css" "/css/typeahead.css") 
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#startDate" startDate min max))
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#endDate" endDate min max))
      (javascript-tag (util/render-javascript-template "templates/load-chart.js" "#render-chart" base-url id))
      (javascript-tag "$('#visType').change(function(){
                            var params=$('#changepoint-parameter'); 
                            if('changepoints.png'==$(this).val()){ params.slideDown();} else{ params.slideUp();}})")
      (javascript-tag "$('#visType').change(function(){
                            var params=$('#entropy-parameter');
                            var val=$(this).val(); 
                            if(val.slice(0,7)=='entropy'){ params.slideDown();} else{ params.slideUp();}})")
      (javascript-tag (format 
                        "$('#sensor').typeahead({
                             name: 'sensor-numerator',
                           engine: Hogan,
                         prefetch: '%s',
                            limit: 10,
                         template: '<p>{{component}}/{{name}}</p><p>({{type}})</p>'})"
                        (resolve-url (format "/data/%s/names.json" id))))
      (if (params :run) 
        (javascript-tag "$(document).ready(function(){$('#render-chart').trigger('click');})")))))

(defpage [:post "/series-of/:id"] {:keys [id] :as params}
  (render-series-page id (merge default-series-settings (map-values keyword identity params))))

(defpage all-series "/series-of/:id" {:keys [id params]}
  (render-series-page id (merge default-series-settings (map-values keyword identity (json/parse-string params)))))

;;;;;;;;;;;;;;;;;; components of a plant ;;;;;;;;;;;;;;;;;;;;;;
(defn- convert-node [node]
  (let [name (get-in node [:attrs :name])
        type (get-in node [:attrs :type])
        data {:data (json/generate-string {:attr (or (:attrs node) {})
                                           :type type
                                           :name name})}]
    (if (:content node)
      [:li.folder data (str type " - " name)]
      [:li data (str type" - " name)])))

(defn- convert-structure-node [node]
  (cond 
    (:content node) (conj (convert-node node ) (apply vector :ul (:content node)))
    (and (-> node :attrs :name) (-> node :attrs :type)) (convert-node node) 
    :else node))

(defn- render-components-tree [structure]
  [:ul {:style "display:none;"} 
   (clojure.walk/postwalk convert-structure-node structure)])

(defpage structure-page "/structure/:id" {:keys [id]}
  (let [structure (db/structure-of id)]
    (common/layout-with-links
      (toolbar-links id 2)
      [:div.span3
       [:h4 "Komponentenstammbaum"]
       [:div#components-tree
        (render-components-tree structure)
        ]]
      [:div.span9
       [:h4 "Detailangaben"]
       [:table#details]]
      (hiccup.page/include-js "/js/jquery-ui.min.js" "/js/jquery.dynatree.min.js")
      (hiccup.page/include-css "/css/dynatree/ui.dynatree.css")
      (javascript-tag  
        "$(document).ready(function() { 
           $('#components-tree').dynatree({
           checkbox:false,
           selectMode: 3,          
           persist: false,
           onActivate: function(node) { n=node;
              var s='<table id=\"details\" class=\" table table-striped table-condensed\">';
              for(p in node.data.attr) s+='<tr><td>'+p+'</td><td>'+node.data.attr[p]+'</td></tr>';
              s+='</table>';
              $('#details').replaceWith(s);
           },
           minExpandLevel: 1})});"))))

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id (url-encode id)}) "Allgemeines")
   (link-to (url-for structure-page {:id (url-encode id)}) "Komponenten")
   (link-to (url-for all-series {:id (url-encode id)}) "Messwerte")]
  )

(defpage string-status "/status/:id" {:keys [id]}
  (common/layout-with-links 
    (toolbar-links id nil)
    [:div.span2]
    [:div.span6
     [:div#matrix]]
    [:div.span4
     [:span#entropyText]]
    [:style ".background {
  fill: #eee;
}

line {
  stroke: #fff;
}

text.active {
  fill: red;
  font-weight: bold;
  visibility: inherit;
  fill-opacity: 1;
}
.cellLabel {
  font-size:smaller;
  /*visibility: hidden;*/
}
.cell {
  cursor: pointer;
  fill-opacity: 0.5;
}"]
     (hiccup.page/include-js "/js/chart/d3.v2.min.js")
     (javascript-tag (util/render-javascript-template "templates/matrix.js" (util/base-url) "#matrix" id))))

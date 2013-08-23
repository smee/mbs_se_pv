(ns mbs-se-pv.views.timeseries
    (:require 
      [clojure 
       [string :as string]]
      [mbs-se-pv.views 
       [common :as common]
       [charts :as ch]
       [util :as util :refer [t]]
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
  [:first-date 
   :last-date 
   :id 
   :anlagenkwp 
   :anzahlwr 
   :hpmodul  
   :hpwr 
   :hpausricht])
(def ^:private personal-labels
  [:hpbetreiber 
   :verguetung 
   :hpstandort 
   :hppostleitzahl 
   :hpemail  
   :hpinbetrieb 
   :serialnr])

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
     (for [label-key labels 
           :let [label (t  (->> label-key name (str "mbs-se-pv.views.timeseries/") keyword))
                 value (metadata label-key)] 
           :when value]
       [:tr [:th.span4 label] [:td (metadata-value label-key value)]])]))


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
        w 600
        h 300]
    (common/layout-with-links 
      (toolbar-links plant 1)
      nil
      [:div.span6
        [:h3 (t ::header-plant)]
        (metadata-table metadata installation-labels)
        [:h3 (t ::header-operator)]
        (metadata-table metadata personal-labels)
        [:a.btn.btn-large.btn-success {:href (resolve-url (url-for all-series {:id plant}))} (t ::measurements)]
        [:h3 (t ::header-data-per-day)]
        [:div#calendar.span9
         [:div.controls
          (drop-down "calendar-data" 
                     [[(t ::missing-data) (resolve-url (url-for cal/missing-data {:id (url-encode plant)}))]
                      [(t ::maintenance) (resolve-url (url-for cal/maintainance-dates {:id (url-encode plant)}))]
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
        [:h3 (t ::header-gain-year)]
        [:h4 (t ::header-gain-day)]
        (render-gain-image plant one-year-ago today w h "day")
        [:h4 (t ::header-gain-week)]
        (render-gain-image plant one-year-ago today w h "week")
        [:h4 (t ::header-gain-month)]
        (render-gain-image plant one-year-ago today w h "month")]
       (common/include-css "/css/colorbrewer.css")
       (common/include-js "/js/chart/d3.v2.min.js") 
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
  (if (re-matches #"\w+/\w+\w{4}\d*\..*" n) 
    (let [[ld-name ln-name & _] (string/split n #"/|\.")
          [prefix ln-name id] (extract-ln-name ln-name)]
      (remove empty? [ld-name ln-name id prefix]))
    (remove empty? (string/split n #"/"))))

(defn- restore-physical-hierarchy [names] 
  (let [iec (keys names)
        splitted (map split-iec-name iec)
        labels-and-names (map #(vector (get-in names [% :name]) %) iec)] 
    (util/restore-hierarchy (map #(into (vec %1) %2) splitted labels-and-names))))

(defn- split-iec-name-typed [[n {type :type}]] 
  (if (re-matches #"\w+/\w+\w{4}\d*\..*" n)
    (let [[ld-name ln-name & _] (string/split n #"/|\.")
          [prefix ln-name id] (extract-ln-name ln-name)]
      (remove empty? [type ld-name id prefix]))
    (remove empty? (cons (if (or (nil? type) (= "" type)) (t ::unknown) type) (string/split n #"/")))))

(defn- cluster-by-type [names]
  (let [iec (keys names)
        splitted (map split-iec-name-typed names)
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
        by-phys {(t ::by-component) (restore-physical-hierarchy names)}
        by-type {(t ::by-physical-type) (cluster-by-type names)} 
        tree (->> by-phys
               (merge by-type)               
               (postwalk #(if (map? %) (into (sorted-map) %) %))
               #_(postwalk #(if (map? %) (reduce (fn [m[k v]] 
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
   :minHour 10
   :maxHour 15
   :bins 500
   :days 30
   :skipMissing true
   :sensor ""
   :width 950
   :height 600
   :threshold 1.3
   :num 2
   :useRawEntropy true})

(defn- render-series-page [id {:keys [startDate endDate] :as params}]
  (let [c (db/count-all-series-of-plant id)
        names (db/all-series-names-of-plant id)
        {:keys [min max]} (db/min-max-time-of id (ffirst names))
        startDate (or startDate (.format (util/dateformat) max)) 
        endDate (or endDate (.format (util/dateformat) max)) 
        base-url (util/base-url)]

    (common/layout-with-links
      (toolbar-links id 4)
      ;; sidebar
      [:div.span3
       [:form.form-vertical.well 
        [:div
         [:h4 (t ::date)]
         [:div.input-prepend 
          [:span.add-on (t ::starting)] 
          (text-field {:placeholder (t ::start-date) :class "input-small"} "startDate" startDate)]
         [:div.input-prepend
          [:span.add-on (t ::ending)]
          (text-field {:placeholder (t ::end-date) :class "input-small"} "endDate" endDate)]
         [:div
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(-1,0,0,0)"} (t ::minus-day)]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(-7,0,0)"} (t ::minus-week)]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,-1,0)"} (t ::minus-month)]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,0,-1)"} (t ::minus-year)]]
         [:div
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(1,0,0,0)"} (t ::plus-day)]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(7,0,0)"} (t ::plus-week)]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,1,0)"} (t ::plus-month)]
          [:a.btn.btn-mini {:href "#" :onclick "DateSelector.shiftTime(0,0,1)"} (t ::plus-year)]]
         [:label.checkbox (check-box "rerender" true) (t ::draw-automatically)]]
        [:div
         [:h4 (t ::data-series)]
         (series-tree id names "series-tree" (set (params :selectedSeries)))]
        [:div
         [:h4 (t ::kind-of-view)]
         [:div.controls
          (drop-down "visType" [[(t ::interactive-view), "dygraph.json"] 
                                   [(t ::static-view) "chart.png"]
                                   [(t ::heatmap) "heat-map.png"]
                                   [(t ::heatmap-pair) "relative-heat-map.png"]
                                   [(t ::ratio) "dygraph-ratios.json"] 
                                   [(t ::difference) "dygraph-differences.json"] 
                                   [(t ::entropy-change-simple) "entropy.json"]
                                   [(t ::entropy-change-matrix) "entropy-bulk.json"]
                                   [(t ::change-point) "changepoints.png"]
                                   [(t ::unusual-day) "discord.png"]
                                   [(t ::correlation) "correlation.png"]]
                     (params :visType))]]
        [:div#changepoint-parameter {:style (str "display:" (if (= (params :visType) "changepoints.png") "block" "none"))}
         [:h4 (t ::misc-parameters)]
         [:div.controls
          [:label.checkbox (check-box :rank (params :rank)) (t ::use-ranks)]
          [:label.checkbox (check-box :zero (params :zero)) (t ::erase-zeroes)]
          [:label.checkbox (check-box :negative (params :negative)) (t ::show-drops-only)]
          [:label.checkbox (check-box :maintenance (params :maintenance)) (t ::ignore-maintenances)]]
         [:div.input-prepend
          [:span.add-on (t ::confidence)]
          (text-field {:placeholder (t ::p-value) :class "input-small"} "confidence" (params :confidence))]
         [:div.input-prepend
          [:span.add-on (t ::level)]
          (text-field {:placeholder (t ::max-level) :class "input-small"} "maxLevel" (params :maxLevel))]]
        [:div#discord-parameter {:style (str "display:" (if (= (params :visType) "discord.png") "block" "none"))}
         [:div.input-prepend
          [:span.add-on (t ::count)]
          (text-field {:class "input-small"} "num" (params :num))]] 
        [:div#entropy-parameter {:style (str "display:" (if (= (params :visType) "entropy.json") "block" "none"))} 
         [:h4 (t ::misc-parameters)]
         [:div.input-prepend
          [:span.add-on (t ::min)]
          (text-field {:class "input-small"} "minHist" (params :minHist))]
         [:div.input-prepend
          [:span.add-on (t ::max)]
          (text-field {:class "input-small"} "maxHist" (params :maxHist))]
         [:div.input-prepend
          [:span.add-on (t ::starting-hour)]
          (text-field {:class "input-small"} "minHour" (params :minHour))]
         [:div.input-prepend
          [:span.add-on (t ::ending-hour)]
          (text-field {:class "input-small"} "maxHour" (params :maxHour))]
         [:div.input-prepend
          [:span.add-on (t ::bins)]
          (text-field {:class "input-small"} "bins" (params :bins))]
         [:div.input-prepend
          [:span.add-on (t ::days)]
          (text-field {:class "input-small"} "days" (params :days))]
         [:div.input-prepend
          [:span.add-on (t ::threshold)]
          (text-field {:class "input-small"} "threshold" (params :threshold))]
         [:div.controls
          [:label.checkbox (check-box :useRawEntropy (params :useRawEntropy)) (t ::relative-entropy)]]         
         [:div.controls
          [:label.checkbox (check-box :skipMissing (params :skipMissing)) (t ::ignore-gaps)]]         
         [:div.input-prepend
          [:span.add-on (t ::sensor-name)]
          (text-field {:class "input-large" :placeholder (t ::sensor-name-hint)} "sensor" (params :sensor))]]
        (hidden-field "highlightSeries" (:highlightSeries params)) 
        [:div
         [:h4 (t ::size)]
         (text-field {:type "number" :class "input-mini"} "width" (params :width))
         [:span "X"]
         (text-field {:type "number" :class "input-mini"} "height" (params :height))
         [:span "px"]]
        [:button#render-chart.btn-primary.btn-large 
         [:i.icon-picture.icon-white]
         (t ::show)]
        ]]
      ;; main content
      [:div.span9        
       [:h2 (t ::chart-header)]
       [:div#current-chart (t ::chart-hint)]]
      ;; render calendar input via jquery plugin
      (common/include-js "/js/jquery-ui.min.js" 
                              "/js/jquery.dynatree.min.js" 
                              "/js/datepicker.js"
                              "/js/chart/dygraph-combined-dev.js"
                              "/js/chart/dygraph-functions.js"
                              "/js/chart/d3.v2.min.js"
                              "/js/chart/matrix.js"
                              "/js/chart/heatmap.js"
                              "/js/hogan-2.0.0.js"
                              "/js/typeahead.js")
      (common/include-css "/css/dynatree/ui.dynatree.css" "/css/datepicker.css" "/css/typeahead.css") 
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#startDate" startDate min max))
      (javascript-tag (util/render-javascript-template "templates/date-selector.js" "#endDate" endDate min max))
      (javascript-tag (util/render-javascript-template "templates/load-chart.js" "#render-chart" base-url id))
      (javascript-tag "$('#visType').change(function(){
                            var val=$(this).val(); 
                            var entropyparams=$('#entropy-parameter');
                            var cpparams=$('#changepoint-parameter'); 
                            var discordparams=$('#discord-parameter'); 

                            if('changepoints.png'==val){ cpparams.slideDown(); }else{ cpparams.slideUp();};
                            if(val.slice(0,7)=='entropy'){ entropyparams.slideDown(); }else{ entropyparams.slideUp();}
                            if('discord.png'==val){ discordparams.slideDown(); }else{ discordparams.slideUp();}})")
      (javascript-tag (format 
                        "$('#sensor').typeahead({
                             name: 'sensor-numerator-%s',
                           engine: Hogan,
                         prefetch: '%s',
                            limit: 10,
                         template: '<p>{{component}}/{{name}}</p><p>({{type}})</p>'})"
                        id (resolve-url (format "/data/%s/names.json" id))))
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
       [:h4 (t ::component-tree)]
       [:div#components-tree
        (render-components-tree structure)
        ]]
      [:div.span9
       [:h4 (t ::details)]
       [:table#details]]
      (common/include-js "/js/jquery-ui.min.js" "/js/jquery.dynatree.min.js")
      (common/include-css "/css/dynatree/ui.dynatree.css")
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
  (let [ps {:id (url-encode id)}] 
    [active-idx
     (link-to "/" (t ::overview))
     (link-to (url-for metadata-page ps) (t ::general))
     (link-to (url-for structure-page ps) (t ::components))
     (link-to {:class "btn-info"} (str "/status/" (url-encode id)) (t ::state))
     (link-to (url-for all-series ps) (t ::measurements))]))


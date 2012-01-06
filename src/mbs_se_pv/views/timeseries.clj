(ns mbs-se-pv.views.timeseries
    (:require 
      [clojure.string :as string]
      [mbs-se-pv.views 
       [common :as common]
       [charts :as ch]]
      [mbs-db.core :as db])
    (:use noir.core
          hiccup.core
          hiccup.page-helpers
          hiccup.form-helpers
          mbs-se-pv.views.util
          [org.clojars.smee.map :only (map-values)]))

(declare toolbar-links)

;;;;;;;;;;;;;;;;;;;;;;;; show metadata as table ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- metadata-table [metadata]
  (let [wr-details (:wr metadata)
        metadata (dissoc metadata :wr)
        k (sort (keys metadata))]
    [:table.condensed-table.zebra-striped 
     (for [[k v] (into (sorted-map) metadata)]
       [:tr [:th k] [:td v]])]))

(defpage metadata-page "/details/:id" {name :id}
  (let [metadata (-> name db/get-metadata first second)]
    (common/layout-with-links 
      (toolbar-links name 1)
      nil
      [:div.row
       (metadata-table metadata)])))

;;;;;;;;;;;;;; show all available time series info per pv installation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- label-for-type [s]
  (case s
    "pdc" (list "P" [:sub "DC"])
    "pac" (list "P" [:sub "AC"])
    "temp" "Temperatur"
    "udc" (list "U" [:sub "DC"])
    "efficiency" "&eta;"
    "gain" "Tagesertragsverlauf"
    "daily-gain" "Ertrag pro Tag"
     s))

(defn- nice-labels [parts]
  (let [parts (-> parts
                (update-in [1] #(list "WR" [:sub %]))
                (update-in [2] label-for-type))]
    (if (< 3 (count parts))
      (update-in parts [(dec (count parts))] #(list "String" [:sub %]))
      parts)))

(defn- split-series-name [n]
  (conj (->> #"\."
          (string/split n)
          (remove #{"wr" "string"})
          vec
          nice-labels)
        n))

(defn- restore-wr-hierarchy [names]
  (let [parts (map split-series-name names)]
    (restore-hierarchy parts)))

(defn- cluster-by-type [names]
  (let [parts (map split-series-name names)
        n (ffirst parts)
        get-type #(drop 2 (butlast %))
        cluster (->> parts
                  (group-by get-type)
                  (map-values #(map (juxt second last) %))
                  (mapcat (fn [[t vs]] (for [v vs] (concat [n] t v)))))]
    (restore-hierarchy cluster)))

(defn- make-tree [nested]
  [:ul 
   (for [[k vs] nested]
     (if (and (sequential? vs) (= 1 (count vs)))
       [:li {:data (format "{series: '%s'}" (first vs))} k]
       [:li {:class "folder"} k (make-tree vs)]))])


(defpage all-series "/series-of/:id" {id :id}
  (let [q (str id ".%")
        c (db/count-all-series-of q)
        names (db/all-series-names-of q)
        {:keys [min max]} (db/min-max-time-of (first names))
        date (.format (dateformat) max)
        efficiency-names (distinct (map #(str id ".wr." (extract-wr-id %) ".efficiency") names))
        daily-gain-names (distinct (map #(str id ".wr." (extract-wr-id %) ".daily-gain") names))
        tree (->> names
               (concat efficiency-names daily-gain-names)
               sort
               reverse
               ((juxt restore-wr-hierarchy cluster-by-type))
               (apply concat)
               ;(clojure.walk/postwalk #(if (map? %) (into (sorted-map) %) %))
               make-tree)
        base-url (or hiccup.core/*base-url* "")]
        
    (common/layout-with-links
      (toolbar-links id 2)
      ;; sidebar
      (list 
        [:h5 "Datum"]
        [:p#date-picker]
        [:h5 "Datenreihen"]
        (vector :div#series-tree tree)
        [:div.form-stacked 
         [:h5 "Größe:"]
         [:input#chart-width.miniTextfield {:value "700" :type "number"}] 
         [:span "X"] 
         [:input#chart-height.miniTextfield {:value "600" :type "number"}]]
        [:a {:href ""
             :class "btn primary" 
             :onclick (str 
                        ;; create selected date interval: yyyyMMdd-yyyyMMdd
                        "var dates = $('#date-picker').DatePickerGetDate(false);
                         var m1=dates[0].getMonth()+1;
                         var m2=dates[1].getMonth()+1;
                         var d1=dates[0].getDate();
                         var d2=dates[1].getDate();
                         var startDate=''+dates[0].getFullYear()+(m1<10?'0'+m1:m1)+(d1<10?'0'+d1:d1);
                         var endDate  =''+dates[1].getFullYear()+(m2<10?'0'+m2:m2)+(d2<10?'0'+d2:d2);
                         var interval=startDate+'-'+endDate;"
                        ;; create list of selected time series                        
                        "var selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node){ return node.data.series; });"
                        ;; do not fetch a chart without any selected series
                        "if(selectedSeries.length < 1) return false;"
                        ;; create link  
                        "var link='" base-url "/series-of/" id "/'+selectedSeries.join('/')+'/'+interval+'/chart.png?width='+$('#chart-width').val()+'&height='+$('#chart-height').val();
                         $('#current-chart').toggleClass('loading',true);" 
                        ;; show chart  
                        "$('#chart-image').attr('src', link);
                        return false;")} 
         "Anzeigen"])       
      ;; main content
      [:div.row 
       [:div.span12        
        [:h3 "Chart"]
        [:div#current-chart
         [:img#chart-image {:src ""}]]]]
      ;; render tree via jquery plugin
      (javascript-tag 
        "$('#series-tree').dynatree({
          checkbox:true,
          selectMode: 3,          
          persist: false,
          minExpandLevel: 2});")
      ;; render calendar input via jquery plugin
      (javascript-tag
        (format
          "$('#date-picker').DatePicker({
                  format: 'd.m.Y',
                  date: ['%s', '%s'],
                  current: '%s',
                  calendars: 1,
                  flat: true,
                  mode: 'range',
                  onRender: function(date){
                            return {
                                   disabled: date.valueOf()<%d || date.valueOf()>%d,
                                   className: false
                                  }}});"
          date
          date
          date
          min
          max)))))

(defn- toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id id}) "Allgemeines")
   (link-to (url-for all-series {:id id}) "Messwerte")]
  )


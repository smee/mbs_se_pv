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
    (map #(concat ["nach Bauteil"] (nice-labels (split-series-name %)) [%]))
    restore-hierarchy))

(defn- cluster-by-type [names]
  (->> names
    (map #(let [parts (-> % split-series-name nice-labels)] 
            (concat ["nach Datentyp"] (vector (last parts)) (butlast parts) [%])))
    restore-hierarchy))

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
        [:a.btn.primary {:href ""
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
         "Anzeigen"]
        [:a.btn {:href "#"
             :onlick (format "
var dates = $('#date-picker').DatePickerGetDate(false);
var month=dates[1].getMonth()+1;
var year=dates[1].getFullYear();
var link='%s/report/%s/'+year+'/'+month;
window.open(link);
return false;" 
                             base-url id)} "Report Wirkungsgrad"])       
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

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id id}) "Allgemeines")
   (link-to (url-for all-series {:id id}) "Messwerte")]
  )


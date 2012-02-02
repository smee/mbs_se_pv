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

(defn- metadata-table [metadata]
  (let [wr-details (:wr metadata)
        metadata (dissoc metadata :wr)
        k (sort (keys metadata))]
    [:table.condensed-table.zebra-striped 
     (for [[k v] (into (sorted-map) metadata) :let [label (metadata-label k)] :when label]
       [:tr [:th label] [:td v]])]))

(defpage metadata-page "/details/:id" {name :id}
  (let [metadata (-> name db/get-metadata first second)]
    (common/layout-with-links 
      (toolbar-links name 1)
      nil
      [:div.row
       [:span6 (metadata-table metadata)]
       [:span6 "foo"]])))

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


(defn- date-selection-javascript [id date min max]
  (str
          "$('#" id "').DatePicker({
                  format: 'd.m.Y',
                  date: '" date "',
                  current: '" date "',
                  calendars: 1,
                  onBeforeShow: function(){
                       $('#" id "').DatePickerSetDate($('#" id "').val(), true);
                  },
                  onChange: function(formatted, date) {
                       $('#" id "').val(formatted).DatePickerHide();
                  },
                  onRender: function(date){
                            return {
                                   disabled: date.valueOf()<" min " || date.valueOf()>" max ",
                                   className: false
                                  }}});"))

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
      [:form.form-stacked 
        [:h5 "Datum"]
        (text-field {:placeholder "Startdatum" :class "span2"} "start-date" date)
        (text-field {:placeholder "Enddatum" :class "span2"} "end-date" date)
        [:h5 "Datenreihen"]
        (vector :div#series-tree tree)
        [:div 
         [:h5 "Größe:"]
         [:input#chart-width.span2 {:value "850" :type "number"}] 
         [:span "X"] 
         [:input#chart-height.span2 {:value "700" :type "number"}]]
        [:a.btn.primary {:href ""
             :onclick (str 
                        ;; create selected date interval: yyyyMMdd-yyyyMMdd
                        "var start = $('#start-date').DatePickerGetDate(false);
                         var end = $('#end-date').DatePickerGetDate(false);
                         var m1=start.getMonth()+1;
                         var m2=end.getMonth()+1;
                         var d1=start.getDate();
                         var d2=end.getDate();
                         var startDate=''+start.getFullYear()+(m1<10?'0'+m1:m1)+(d1<10?'0'+d1:d1);
                         var endDate  =''+end.getFullYear()+(m2<10?'0'+m2:m2)+(d2<10?'0'+d2:d2);
                         var interval=startDate+'-'+endDate;"
                        ;; create list of selected time series                        
                        "var selectedSeries = $.map($('#series-tree').dynatree('getSelectedNodes'), function(node){ return node.data.series; });"
                        ;; do not fetch a chart without any selected series
                        "if(selectedSeries.length < 1) return false;"
                        ;; create link  
                        "var link='" base-url "/series-of/" id "/'+selectedSeries.join('/')+'/'+interval+'/chart.png?width='+$('#chart-width').val()+'&height='+$('#chart-height').val();
                         $('#current-chart').showLoading();" 
                        ;; show chart  
                        "$('#chart-image').attr('src', link).load(function(){
$('#current-chart').hideLoading();});
                        return false;")} 
         "Anzeigen"]
        [:a.btn {:href "#"
             :onclick (format "
var date = $('#start-date').DatePickerGetDate(false);
var month=date.getMonth()+1;
var year=date.getFullYear();
var link='%s/report/%s/'+year+'/'+month;
window.open(link);
return true;" 
                             base-url id)} "Report Wirkungsgrad"]]       
      ;; main content
      [:div.row 
       [:div.span12        
        [:h2 "Chart"]
        [:div#current-chart "Bitte wählen Sie links die zu visualisierenden Daten und ein Zeitinterval aus."
         [:img#chart-image {:src ""}]]]]
      ;; render tree via jquery plugin
      (javascript-tag 
        "$('#series-tree').dynatree({
          checkbox:true,
          selectMode: 3,          
          persist: false,
          minExpandLevel: 2});")
      ;; render calendar input via jquery plugin
      (javascript-tag (date-selection-javascript "start-date" date min max))
      (javascript-tag (date-selection-javascript "end-date" date min max)))))

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to "/" "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id id}) "Allgemeines")
   (link-to (url-for all-series {:id id}) "Messwerte")]
  )


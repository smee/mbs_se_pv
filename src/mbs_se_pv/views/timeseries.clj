(ns mbs-se-pv.views.timeseries
    (:require 
    [clojure.string :as string]
    [mbs-se-pv.views 
     [common :as common]
     [charts :as ch]]
    [mbs-se-pv.models.db :as db])
  (:use noir.core
        hiccup.core
        hiccup.page-helpers
        hiccup.form-helpers
        mbs-se-pv.views.util))

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
  (let [metadata (db/get-metadata name)]
    (common/layout-with-links 
      (toolbar-links name 1)
      nil
      [:div.row
       (metadata-table metadata)])))

;;;;;;;;;;;;;; show date selector for all days of a single time series ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defpage stats "/series-of/:id/single/*/date-selector" {:keys [id *]}
  (let [name *
        wr-id (extract-wr-id name)
        efficiency-chart? (.endsWith name ".efficiency")
        {:keys [min max]} (if efficiency-chart?
                            (db/min-max-time-of (str id ".wr." wr-id ".pac"))
                            (db/min-max-time-of name))
        date (.format (dateformat) max)
        link-template (if efficiency-chart?
                        (resolve-uri (format "/series-of/%s/efficiency/%s" id wr-id))
                        (resolve-uri (format "/series-of/%s/%s" id name)))
        onclick-handler "$('#chart-image').removeClass('hide').attr('src', this.options[this.selectedIndex].value);"
        ] 
    (html
      [:strong "Bitte w&auml;hlen Sie ein Datum:"]
      [:p#date-picker]
      )))

;;;;;;;;;;;;;; show all available time series info per pv installation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- restore-wr-hierarchy [series]
  (let [parts (map #(conj (->> #"\."
                            (string/split %)
                            (remove #{"wr" "string"})
                            vec)
                          %) 
                   series)]
    (restore-hierarchy parts)))

(defn- make-tree [nested]
  [:ul 
   (for [[k vs] nested]
     (if (sequential? vs)
       [:li {:data (format "{series: '%s'}" (first vs))} k]
       [:li {:class "folder"} k (make-tree vs)]))])


(defpage all-series "/series-of/:id" {id :id}
  (let [q (str id ".%")
        c (db/count-all-series-of q)
        names (db/all-series-names-of q)
        {:keys [min max]} (db/min-max-time-of (first names))
        date (.format (dateformat) max)
        efficiency-names (distinct (map #(str id ".wr." (extract-wr-id %) ".efficiency") names))]
        
    (common/layout-with-links
      (toolbar-links id 2)
      ;; sidebar
      (list 
        [:h5 "Datum"]
        [:p#date-picker]
        [:h5 "Datenreihen"]
        (->> names
          (concat efficiency-names)
          restore-wr-hierarchy
          (clojure.walk/postwalk #(if (map? %) (into (sorted-map) %) %))
          make-tree
          (vector :div#series-tree))
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
                        ;; create link  
                        "var link='/series-of/" id "/'+selectedSeries.join('/')+'/'+interval+'/chart.png';
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
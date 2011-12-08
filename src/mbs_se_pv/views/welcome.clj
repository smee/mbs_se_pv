(ns mbs-se-pv.views.welcome
  (:require 
    [clojure.string :as string]
    [mbs-se-pv.views 
     [common :as common]
     [charts :as ch]]
    [mbs-se-pv.models.db :as db])
  (:use noir.core
        [noir.response :only (redirect)]
        hiccup.core
        hiccup.page-helpers
        hiccup.form-helpers
        [org.clojars.smee
         [map :only (map-values)]
         [util :only (s2i)]]
        mbs-se-pv.views.util)
  (:import java.util.Calendar))

(declare toolbar-links)

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
                        (resolve-uri (format "/series-of/%s/single/%s" id name)))
        onclick-handler "$('#chart-image').removeClass('hide').attr('src', this.options[this.selectedIndex].value);"
        ] 
    (html
      [:strong "Bitte w&auml;hlen Sie ein Datum:"]
      [:p#date-picker]
      (javascript-tag
        (format
          "$('#replace-me').fadeIn(100);
           $('#date-picker').DatePicker({
                  format: 'd.m.Y',
                  date: ['%s', '%s'],
                  current: '%s',
                  calendars: 3,
                  flat: true,
                  mode: 'range',
                  onBeforeShow: function(){
                            var dates = $('#date-picker').DatePickerGetDate(false);
                            console.log(dates);
                  },
                  onChange: function(formated, dates){
                            var m1=dates[0].getMonth()+1;
                            var m2=dates[1].getMonth()+1;
                            var d1=dates[0].getDate();
                            var d2=dates[1].getDate();
                            var startDate=''+dates[0].getFullYear()+(m1<10?'0'+m1:m1)+(d1<10?'0'+d1:d1);
                            var endDate  =''+dates[1].getFullYear()+(m2<10?'0'+m2:m2)+(d2<10?'0'+d2:d2);
                            var link='%s'+'/'+startDate+'-'+endDate+'/chart.png';
                            $('#chart-image').removeClass('hide').attr('src',link);
                          },
                  onRender: function(date){
                            return {
                                   disabled: date.valueOf()<%d || date.valueOf()>%d,
                                   className: false
                                  }}});"
          date
          date
          date
          link-template
          min
          max)))))

;;;;;;;;;;;;;; show all available time series info per pv installation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- restore-wr-hierarchy [name series]
  (let [links (map #(resolve-uri (url-for stats {:id name :* %})) series)
        parts (map #(concat (remove #{"wr" "string"} (string/split % #"\.")) [%2]) series links)]
    (restore-hierarchy parts)))

(defn- make-tree [nested]
  [:ul 
   (for [[k vs] nested]
     (if (sequential? vs)
       [:li [:a {:href (first vs)} k]]
       [:li {:class "folder"} k (make-tree vs)]))])

(defn- metadata-table [metadata]
  (let [wr-details (:wr metadata)
        metadata (dissoc metadata :wr)
        k (sort (keys metadata))]
    [:table.condensed-table.zebra-striped 
     (for [[k v] (into (sorted-map) metadata)]
       [:tr [:th k] [:td v]])]))

(defpage all-series "/series-of/:id" {arg :id}
  (let [q (str arg ".%")
        c (db/count-all-series-of q)
        names (db/all-series-names-of q)
        efficiency-names (distinct (map #(str arg ".wr." (extract-wr-id %) ".efficiency") names))
        names (concat names efficiency-names)]
        
    (common/layout-with-links
      (toolbar-links arg 2)
      [:div.row
       [:span.span4 
        (->> names
          (restore-wr-hierarchy arg)
          (clojure.walk/postwalk #(if (map? %) (into (sorted-map) %) %))
         make-tree
         (vector :div#series-tree))]
       [:div#details.span12
        [:div.row
         [:div#replace-me.span4]]
        [:div.row 
         [:div#chart.span8
          [:div [:h3 "Chart"]]
          [:div#current-chart
           [:img#chart-image.hide.loading {:src ""}]]]]]]
      (javascript-tag 
        "$('#series-tree').dynatree({
           onActivate: function(node){ 
                          if( node.data.href )
                            $('#replace-me').fadeOut(100);
                            $('#chart-image').toggleClass('hide',true);
                            $('#replace-me').load(node.data.href);
                          },
          persist: false,
          minExpandLevel: 2});"))))

;;;;;;;;;;;;;;;;;;;;;;;; show metadata as table ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defpage metadata-page "/details/:id" {name :id}
  (let [metadata (db/get-metadata name)]
    (common/layout-with-links 
      (toolbar-links name 1)
      [:div.row
       (metadata-table metadata)])))


;;;;;;;;;;; show all available pv installation names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpartial names-pagination [page len]
  (let [width 5
        start (Math/max 1 (- page width))
        end (Math/max (* 2 width) (+ page width))
        template "/eumonis?page=%d&length=%d"]
    [:div.pagination
     [:ul 
      [:li.prev (link-to (format template (Math/max 1 (dec page)) len) "← vorherige Seite")]
      (for [i (range start end)]
        [:li (link-to (format template i len) (str i))])
      [:li.next (link-to (format template (inc page) len) "n&auml;chste Seite →")]]]))

(defpage start-page "/eumonis" {:keys [page length] :or {page "1" length "20"}}
  (let [page (Math/max 1 (s2i page 1))
        len (->> (s2i length 20) (Math/max 1) (Math/min 50))
        links (map #(link-to (url-for details-page {:id %}) %) (db/all-names-limit (* page len) len))] 
    (common/layout-with-links 
      (toolbar-links "#" 0)
      (names-pagination page len)
      [:table#names.zebra-striped.condensed-table
       [:thead [:tr [:th "Anlagenbezeichnung"]]]
       [:tbody 
        (for [link links]
          [:tr [:td link]])]]
      (names-pagination page len))))

(defpage "/" []
  (redirect (url-for start-page)))

(defn toolbar-links 
  "Links for the toolbar, see common/eumonis-topbar or common/layout-with-links for details"
  [id active-idx]
  [active-idx
   (link-to (url-for start-page) "&Uuml;bersicht")
   (link-to (url-for metadata-page {:id id}) "Allgemeines")
   (link-to (url-for all-series {:id id}) "Messwerte")]
  )
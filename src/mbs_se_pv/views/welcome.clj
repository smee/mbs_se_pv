(ns mbs-se-pv.views.welcome
  (:require 
    [clojure.zip :as z]
    [clojure.string :as string]
    [mbs-se-pv.views [common :as common]
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

(defn dates-seq 
  "Create lazy sequence of all dates in the given interval."
  [start-date end-date] 
  {:pre [(< start-date end-date)]}
  (let [cal (doto (Calendar/getInstance)
              (.setTimeInMillis start-date);
              (.add Calendar/DAY_OF_MONTH 1))
        date (.getTimeInMillis cal)]
    (when (< date end-date) 
      (lazy-seq
        (cons date (dates-seq date end-date))))))



;;;;;;;;;;;;;; show all days of a single time series ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defpage stats "/series-of/:id/single/*/charts" {:keys [id *]}
  (let [name *
        {:keys [min max]} (db/min-max-time-of name)] 
    (html
      [:p (format "%s has %d values spanning %s till %s." 
                  name 
                  (db/count-all-values-of name) 
                  (.format (dateformat) min) 
                  (.format (dateformat) max))]
      (ordered-list 
        (for [date (dates-seq min max) 
              :let [end (+ date ONE-DAY)
                    times (format "%s-%s" (.format (dateformatrev) date) (.format (dateformatrev) end))
                    link (resolve-uri (url-for ch/chart-modal {:id id :* name :times times}))
                    script (format "$('#chart-popup').load('%s', function() { $('#chart-popup').modal('show'); })" link)]]
          [:a {:href "#" :onclick script} (.format (dateformat) date)])))))

;;;;;;;;;;;;;; show all available time series info per pv installation ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- restore-wr-hierarchy [name series]
  (let [links (map #(url "/series-of/" name "/single/" % "/charts") series)
        parts (map #(concat (remove #{"wr" "string"} (string/split % #"\.")) [%2]) series links)]
    (restore-hierarchy parts)))

(defn- make-tree [nested]
  [:ul 
   (for [[k vs] nested]
     (if (sequential? vs)
       [:li {:id (str (Math/random))} [:a {:href (first vs)} k]]
       [:li {:id (str (Math/random)) :class "folder"} k (make-tree vs)]))])


(defpage "/series-of/:id" {arg :id}
  (let [q (str arg ".%")
        c (db/count-all-series-of q)
        names (db/all-series-names-of q)]
    
    (common/layout
      [:div.row
       [:span.span4 
        (->> names
          (restore-wr-hierarchy arg)
          (clojure.walk/postwalk #(if (map? %) (into (sorted-map) %) %))
         make-tree
         (vector :div#series-tree))]
       [:div#replace-me.span4]
       [:div#chart-popup.span8.modal.hide.fade {:style "display:none;"}]]
      (javascript-tag 
        "$('#series-tree').dynatree({
           onActivate: function(node){ 
                          if( node.data.href )
                             //window.location=node.data.href;
                            $('#replace-me').fadeOut(200);
                            $('#replace-me').load(node.data.href, function(){$('#replace-me').fadeIn(200);});
                          },
          persist: true,
          minExpandLevel: 2
                      });
        $('#chart-popup').modal({
                         keyboard: true,
                         backdrop: true,
                         show: false
                        });"))))

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
        links (map #(link-to (str "/series-of/" %) %) (db/all-names-limit (* page len) len))] 
    (common/layout 
      (names-pagination page len)
      [:table#names.zebra-striped
       [:thead [:tr [:th "Anlagenbezeichnung"]]]
       [:tbody 
        (for [link links]
          [:tr [:td link]])]]
      (names-pagination page len))))

(defpage "/" []
  (redirect (url-for start-page)))
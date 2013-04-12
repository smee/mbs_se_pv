(ns mbs-se-pv.views.analysis
  (:require 
      [mbs-se-pv.algorithms :as alg] 
      [mbs-se-pv.views 
       [analysis :as analysis] 
       [common :as common]
       [timeseries :as ts] 
       [util :as util]]
      [mbs-db.core :as db])
    (:use [noir 
           core
           [response :only [json]]]
          [ring.util.codec :only (url-encode)]
          [hiccup core element form page]
          [org.clojars.smee 
           [util :only (s2i)]]))

;;;;;;;;;;;;;;;;;;;;;;;; analysis results page ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn flatten-analysis-results [results]
  (for [{{sc-name :name} :scenario timestamp :timestamp alerts :alerts} results
        {:keys [probability id name]} alerts]
    [sc-name timestamp name id probability]))

;; called via ajax, see http://datatables.net/usage/server-side for details
;; queries, filters, slices and dices analysis results
(defpage "/data/:id/events.json" {len :iDisplayLength
                                  start :iDisplayStart
                                  search-term :sSearch
                                  echo :sEcho
                                  sort-col :iSortCol_0
                                  sort-dir :sSortDir_0
                                  id :id
                                  :as query}
  (let [today (System/currentTimeMillis)
        one-year-ago (- today (* 365 24 60 60 1000))
        len (s2i len 10)
        start (s2i start 1)
        col-id (s2i sort-col 1)      
        results  (->> today
                   (alg/find-most-recent-anomalies id one-year-ago)
                   flatten-analysis-results
                   (sort-by #(nth % col-id)))
        df (java.text.SimpleDateFormat. "dd.MM.yyyy")
        results (for [s results] 
                  (-> s 
                    (update-in [1] #(.format df %))
                    (update-in [4] #(format "%.1f" (* 100 %)))))
        sorted (if (= "asc" sort-dir) results (reverse results))
        filtered (filter #(.contains (.toLowerCase ^String (apply str %)) (.toLowerCase search-term)) sorted)
        c (count sorted) 
        spliced (subvec (vec filtered) (min start c) (min (count filtered) (+ len start) c))
        ]
    (json 
      {:iTotalRecords (count sorted)
       :iTotalDisplayRecords (count filtered)
       :sEcho echo
       :aaData spliced})))


(defpage string-status "/status/:id" {:keys [id]}
  (let [scenarios (db/get-scenarios id)]
    (common/layout-with-links 
      (ts/toolbar-links id 3)
;      (unordered-list (map-indexed #(vector :a {:href (str "#analysis-" %)} (:name %2)) scenarios))
      [:div.span12
       [:h1 "Auffälligkeiten der Anlage " \" id \"]
       [:table#anomalies.table.table-striped.table-condensed
        [:thead 
         [:tr 
          [:th "Name des Analyseszenarios"]
          [:th "Datum"]
          [:th "Name des Sensors"]
          [:th "ID des Sensors"]
          [:th "Wahrscheinlichkeit"]]]
        [:tbody]]]
      (hiccup.page/include-js "/js/jquery.dataTables.min.js" "/js/dataTables.paging.bootstrap.js") 
      (javascript-tag (util/render-javascript-template "templates/render-datatable.js" "#anomalies" (str (util/base-url) "/data/" id "/events.json")))
      (map-indexed  
         #(vector :div.widget.pull-left
            [:a {:name (str "analysis-" %1)}]
            [:h1 (str "Analyse: " (:name %2))]
            [:div {:id (str "matrix-" %1)}])
         scenarios)
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
.matrixlabel {
  cursor: pointer;
}
.cell {
  cursor: pointer;
  fill-opacity: 0.5;
}"]
      (hiccup.page/include-js "/js/chart/d3.v2.min.js")
      (map-indexed 
        (fn [idx scenario] 
          (javascript-tag 
            (util/render-javascript-template 
              "templates/matrix.js"
              (util/base-url)
              (format "%s/series-of/%s/%d/20120101-20131231/entropy-bulk.json" (util/base-url) id (-> scenario :id)) 
              (str "#matrix-" idx) 
              id)))
        scenarios))))

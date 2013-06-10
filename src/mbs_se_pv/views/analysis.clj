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
  (let [ONE-MINUTE 60000
        today (* ONE-MINUTE (long (/ (System/currentTimeMillis) ONE-MINUTE))) ; ignore everything within one minute so we do not trash the cache further down the call stack
        one-year-ago (- today (* 365 24 60 60 1000))
        len (s2i len 10)
        start (s2i start 1)
        col-id (s2i sort-col 1)
        scenario2anchor (into {} (map-indexed #(vector (:name %2) (str "#matrix-" %)) (db/get-scenarios id)))
        results  (->> today
                   (alg/find-most-recent-anomalies id one-year-ago) 
                   flatten-analysis-results 
                   (sort-by #(nth % col-id)))
        df (java.text.SimpleDateFormat. "dd.MM.yyyy")
        results (for [s results] 
                  (-> s 
                    (update-in [0] #(html [:a {:href (scenario2anchor %)} %])) 
                    (update-in [1] #(html [:a {:href "#" :onclick (str "EntropyChart.selectByText('" (.format df %) "')")} (.format df %)]))
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
  (let [scenarios (db/get-scenarios id)
        b-u (util/base-url)]
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
      (common/include-js "/js/jquery.dataTables.min.js" 
                         "/js/dataTables.paging.bootstrap.js"
                         "/js/chart/matrix.js") 
      (javascript-tag (util/render-javascript-template "templates/render-datatable.js" "#anomalies" (str b-u "/data/" id "/events.json")))
      [:div.row-fluid
       (map-indexed  
         #(vector :div.widget
                  [:a {:name (str "analysis-" %1)}]
                  [:h1 (str "Analyse: " (:name %2))]
                  [:div {:id (str "matrix-" %1)}])
         scenarios)]
      (common/include-js "/js/chart/d3.v2.min.js")
      (map-indexed 
        (fn [idx scenario] 
          (javascript-tag (format "EntropyChart.createMatrix('%s','%s','%s','%s')" 
                                  b-u 
                                  (format "%s/series-of/%s/%d/20120101-%s/entropy-bulk.json" b-u id (-> scenario :id) (.format (util/dateformatrev) (System/currentTimeMillis)))
                                  (str "#matrix-" idx)
                                  id)))
        scenarios))))

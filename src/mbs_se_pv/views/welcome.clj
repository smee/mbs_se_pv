(ns mbs-se-pv.views.welcome
  (:require 
    [mbs-se-pv.views 
     [common :as common]
     [timeseries :as ts]]
    [mbs-db.core :as db])
  (:use [noir.core :only (defpage defpartial url-for)]
        [noir.response :only (redirect)]
        [hiccup.page-helpers :only (link-to javascript-tag)]
        [org.clojars.smee
         [util :only (s2i)]]
        mbs-se-pv.views.util))


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
        links (map #(link-to (url-for ts/metadata-page {:id %}) %) (db/all-names-limit (* page len) len))] 
    (common/layout 
      (names-pagination page len)
      [:table#names.zebra-striped.condensed-table
       [:thead [:tr [:th "Anlagenbezeichnung"]]]
       [:tbody 
        (for [link links]
          [:tr [:td link]])]]
      (names-pagination page len))))

(defpage "/" []
  (redirect (url-for start-page)))


(ns mbs-se-pv.views.welcome
  (:require 
    [mbs-se-pv.views 
     [common :as common]]
    [mbs-db.core :as db])
  (:use [noir.core :only (defpage defpartial url-for)]
        [noir.response :only (redirect json)]
        [hiccup.core :only (html)]
        [hiccup.page-helpers :only (link-to javascript-tag)]
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

(defpage start-page "/eumonis" []
  (common/layout 
    [:table#names.zebra-striped.condensed-table
     [:thead [:tr [:th "Anlagenbezeichnung"] 
              [:th "Installierte Leistung"]
              [:th "Anzahl Wechselrichter"]]]
     [:tbody]]
    (javascript-tag "
$(document).ready(function() {
	$('#names').dataTable( {
    'sDom': \"<'row'<'span8'l><'span8'f>r>t<'row'<'span8'i><'span8'p>>\",
		'bProcessing': true,
		'bServerSide': true,
		'sAjaxSource': '/metadata.json', 
    'bPaginate': true,
    'sPaginationType': 'bootstrap'
	});
});")))

(defpage "/" []
  (redirect (url-for start-page)))


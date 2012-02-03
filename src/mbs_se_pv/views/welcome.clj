(ns mbs-se-pv.views.welcome
  (:require 
    [mbs-se-pv.views 
     [common :as common]
     [maps :as maps]]
    [mbs-db.core :as db])
  (:use [noir.core :only (defpage defpartial url-for)]
        [noir.response :only (redirect json)]
        [hiccup.core :only (html)]
        [hiccup.page-helpers :only (link-to javascript-tag)]
        mbs-se-pv.views.util))

;;;;;;;;;;; show all available pv installation names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage start-page "/eumonis" []
  (common/layout 
    [:h1 "Anlagenübersicht"]
    [:div.row
     [:div.span10
      [:table#names.zebra-striped.condensed-table
       [:thead [:tr 
                [:th (apply str "Anlagenbezeichnung" (repeat 10 "&nbsp;"))] 
                [:th "Installierte Leistung (kWp)"]
                [:th "Anzahl Wechselrichter"]
                [:th "Postleitzahl"]]]
       [:tbody]]]
     [:div.span6 
      [:h3 "Installierte Leistung pro Postleitzahl"]
      "Bitte doppelt auf eine Region klicken um alle Anlagen darin zu sehen."
      [:div#map]
      (maps/map-includes)
      (javascript-tag (maps/render-plz-map "map" "Reds" "/data/powerdistribution.json" 300000))]
     ]
    (javascript-tag (str "
$(document).ready(function() {
	$('#names').dataTable( {
    'sDom': \"<'row'<'span5 doNotFloat'l><'span5 doNotFloat'f>r>t<'row'<'span3'i><'span7'p>>\",
		'bProcessing': true,
		'bServerSide': true,
		'sAjaxSource': '" (or hiccup.core/*base-url* "") "/data/metadata.json', 
    'bPaginate': true,
    'sPaginationType': 'bootstrap',
    'bStateSave': true,
     'oLanguage': {
			'sLengthMenu': 'Zeige _MENU_ Eintr&auml;ge pro Seite',
			'sZeroRecords': 'Kein Eintrag gefunden!',
			'sInfo': 'Zeige Eintr&auml;ge _START_ bis _END_ von _TOTAL_ vorhandenen',
			'sInfoEmpty': 'Zeige Eintr&auml;ge 0 bis 0 von 0 vorhandenen',
			'sInfoFiltered': '(von insgesamt _MAX_ Eintr&auml;gen)',
      'sProcessing': 'Suche nach passenden Eintr&auml;gen',
      'sSearch': 'Suche:'
		},
    'aoColumns': [
            {}, {sClass: 'alignRight'}, {sClass: 'alignRight'}, {}
            ]
	});
});"))))

(defpage "/" []
  (redirect (url-for start-page)))


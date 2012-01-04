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

(defpage start-page "/eumonis" []
  (common/layout 
    [:table#names.zebra-striped.condensed-table
     [:thead [:tr [:th "Anlagenbezeichnung"] 
              [:th "Installierte Leistung"]
              [:th "Anzahl Wechselrichter"]]]
     [:tbody]]
    (javascript-tag (str "
$(document).ready(function() {
	$('#names').dataTable( {
    'sDom': \"<'row'<'span8'l><'span8'f>r>t<'row'<'span8'i><'span8'p>>\",
		'bProcessing': true,
		'bServerSide': true,
		'sAjaxSource': '" (or hiccup.core/*base-url* "") "/metadata.json', 
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
		}
	});
});"))))

(defpage "/" []
  (redirect (url-for start-page)))


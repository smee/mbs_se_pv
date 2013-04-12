(ns mbs-se-pv.views.welcome
  (:require 
    [mbs-se-pv.views 
     [common :as common]
     #_[maps :as maps]]
    [mbs-db.core :as db]
    [mbs-se-pv.views.timeseries :as ts])
  (:use [noir 
         [core :only (defpage defpartial url-for)]
         [options :only (resolve-url)]
         [response :only (redirect json)]]
        [hiccup 
         [core :only (html)]
         [element :only (link-to javascript-tag)]]
        mbs-se-pv.views.util))

;;;;;;;;;;; show all available pv installation names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage start-page "/eumonis" []
  (common/layout-with-links
    [0
     (link-to (url-for start-page) "&Uuml;bersicht")
     #_(link-to (url-for maps/maps) "Karten")]
    nil  
    [:div.span12
     [:h1 "Anlagenübersicht"]
     [:table#names.table.table-striped.table-condensed
      [:thead [:tr 
               [:th (apply str "Anlagenbezeichnung" (repeat 10 "&nbsp;"))] 
               [:th "Installierte Leistung (kWp)"]
               [:th "Anzahl Wechselrichter"]
               [:th "Postleitzahl"]]]
      [:tbody
       (for [{:keys [id anlagenkwp anzahlwr hppostleitzahl]} (->> (db/get-metadata) vals (take 10))]
         [:tr 
          [:td (link-to (url-for ts/metadata-page {:id id}) id)]
          [:td anlagenkwp]
          [:td hppostleitzahl]
          [:td hppostleitzahl]])]]]
    (hiccup.page/include-js "/js/jquery.dataTables.min.js" "/js/dataTables.paging.bootstrap.js") 
    (javascript-tag (render-javascript-template "templates/render-datatable.js" "#names" (str (base-url) "/data/metadata.json")))))

(defpage "/" []
  (redirect (resolve-url (url-for start-page))))



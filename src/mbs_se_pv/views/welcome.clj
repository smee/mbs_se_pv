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
  (common/layout-with-links
    [0
     (link-to (url-for start-page) "&Uuml;bersicht")
     (link-to (url-for maps/maps) "Karten")]
    nil  
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
    (javascript-tag (render-javascript-template "templates/render-datatable.js" (or hiccup.core/*base-url* "")))))

(defpage "/" []
  (redirect (url-for start-page)))


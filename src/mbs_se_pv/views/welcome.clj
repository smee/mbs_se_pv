(ns mbs-se-pv.views.welcome
  (:require 
    [mbs-se-pv.views 
     [common :as common]
     [maps :as maps]]
    [mbs-db.core :as db]
    [mbs-se-pv.views.timeseries :as ts])
  (:use [noir.core :only (defpage defpartial url-for)]
        [noir.response :only (redirect json)]
        [hiccup.core :only (html resolve-uri)]
        [hiccup.page-helpers :only (link-to javascript-tag)]
        [hiccup.form-helpers :only (drop-down)]
        mbs-se-pv.views.util))

(defpartial render-map []
  [:div.span6 
      [:h3 "Installierte Leistung pro Postleitzahl"]
      [:div "Bitte doppelt auf eine Region klicken um alle Anlagen darin zu sehen."]
      (drop-down {:onchange "mapfn(this.value)"}"mapDataSelector" 
                 [["Anzahl installierter PV-Anlagen" (resolve-uri "/data/installationcounts.json")] 
                  ["Durchschnittliche Einspeisevergütung (cent)" (resolve-uri "/data/averagefee.json")]
                  ["Anzahl installierter Wechselrichter" (resolve-uri "/data/invertercount.json")]
                  ["Installierte Leistung (kW)" (resolve-uri "/data/powerdistribution.json")]
                  ["Erwarter Ertrag (kWh/kWp)" (resolve-uri "/data/averageexpectedgain.json")]
                  ["Anzahl von Siemenswechselrichtern" (resolve-uri "/data/siemenscount.json")]
                  ["Anzahl von Siemenswechselrichtern" (resolve-uri "/data/siemenscount.json")]
                  ["Gerade Postleitzahlen" (resolve-uri "/data/even.json")]
                  ["Ungerade Postleitzahlen" (resolve-uri "/data/odd.json")]]
                 (resolve-uri "/data/powerdistribution.json"))
      [:div#map]
      (maps/map-includes)
      ;; FIXME introduces a global variable 'mapfn' that holds an updater function for the map >:(
      (javascript-tag (str "mapfn="(maps/render-plz-map "map" "RdBu" (resolve-uri "/data/powerdistribution.json") 82000)))])

;;;;;;;;;;; show all available pv installation names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defpage start-page "/eumonis" []
  (common/layout-with-links
    [0
     (link-to (url-for start-page) "&Uuml;bersicht")
     (link-to (url-for maps/maps) "Karten")]
    nil  
    [:h1 "Anlagenübersicht"]
    [:div.row
     [:div.span16
      [:table#names.zebra-striped.condensed-table
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
     ;(render-map)
     ]
    (javascript-tag (render-javascript-template "templates/render-datatable.js" (or hiccup.core/*base-url* "")))))

(defpage "/" []
  (redirect (url-for start-page)))


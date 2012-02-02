(ns mbs-se-pv.views.maps
  (:require 
    [mbs-se-pv.views 
     [common :as common]]
    [mbs-db.core :as db])
  (:use [noir.core :only (defpage defpartial url-for)]
        [noir.response :only (redirect json)]
        [hiccup.core :only (html)]
        hiccup.page-helpers
        mbs-se-pv.views.util
        [clojure.java.io :only (resource reader)]
        [clojure.string :only (join)]))


(defpage "/data/powerdistribution.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        power (zipmap (keys groups) (map #(apply + (map :anlagenkwp %)) (vals groups)))] 
    (json power)))

(defpage "/data/installationcounts.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        power (zipmap (keys groups) (map count (vals groups)))] 
    (json power)))


(defn- map-javascript [div-id color-css-class json-link max-value]
  (let [template (->> "templates/choroplethplz.js" resource reader line-seq (join "\n"))
        base-url (or hiccup.core/*base-url* "")]
    (format template div-id color-css-class color-css-class base-url json-link (int max-value))))

(defpage "/map" {}
  (common/layout 
    (include-css "/css/choroplethplz.css"
                  "http://mbostock.github.com/d3/ex/colorbrewer.css")
     (include-js "http://mbostock.github.com/d3/d3.min.js"
                 "http://mbostock.github.com/d3/d3.geo.min.js")
     [:div.row
      [:div.span8 
       [:h3 "Installierte Leistung"]
       [:div#chart]]
      [:div.span8
       [:h3 "Anzahl installierter PV-Anlagen"]
       [:div#chart2]]] 
     (javascript-tag (map-javascript "chart" "Reds" "/data/powerdistribution.json" 300000))
     (javascript-tag (map-javascript "chart2" "Blues" "/data/installationcounts.json" 10))))

(defpage "/plz/:plz" {plz :plz}
  (let [ids (->> (db/get-metadata) vals (filter #(= plz (:hppostleitzahl %))) (map :id) sort)]
    (common/layout
      [:h3 (str "Alle Anlagen im Postleitzahlenbereich " plz)]
      (unordered-list
        (for [id ids]
          (link-to (str "/details/" id) id))))))
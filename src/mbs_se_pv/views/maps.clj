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

(defpage "/data/averagefee.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        fee (zipmap (keys groups) (for [g (vals groups) :let [n (count g), sum (apply + (map :verguetung g))]] (int (/ sum n))))] 
    (json fee)))

(defpage "/data/invertercount.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        fee (zipmap (keys groups) (map #(apply + (map :anzahlwr %)) (vals groups)))] 
    (json fee)))


(defn render-plz-map [div-id color-css-class json-link max-value]
  (let [base-url (or hiccup.core/*base-url* "")]
    (render-javascript-template "templates/choroplethplz.js" div-id color-css-class base-url json-link (int max-value))))

(defpartial map-includes []
  (include-css "/css/choroplethplz.css"
               "http://mbostock.github.com/d3/ex/colorbrewer.css")
  (include-js "http://mbostock.github.com/d3/d3.min.js"
              "http://mbostock.github.com/d3/d3.geo.min.js"))

(defpage maps "/maps" {}
  (common/layout 
    (map-includes) 
     [:div.row
      [:div.span8 
       [:h3 "Installierte Leistung"]
       [:div#chart]]
      [:div.span8
       [:h3 "Anzahl installierter PV-Anlagen"]
       [:div#chart2]]]
     [:div.row
      [:div.span8
       [:h3 "Durchschnittliche Einspeisevergütung"]
       [:div#chart3]]
      [:div.span8
       [:h3 "Anzahl installierter Wechselrichter"]
       [:div#chart4]]] 
     (javascript-tag (render-plz-map "chart" "Reds" "/data/powerdistribution.json" 300000))
     (javascript-tag (render-plz-map "chart2" "Blues" "/data/installationcounts.json" 10))
     (javascript-tag (render-plz-map "chart3" "Greens" "/data/averagefee.json" 6000))
     (javascript-tag (render-plz-map "chart4" "Oranges" "/data/invertercount.json" 100))))

(defpage "/plz/:plz" {plz :plz}
  (let [ids (->> (db/get-metadata) vals (filter #(= plz (:hppostleitzahl %))) (map :id) sort)]
    (common/layout
      [:h3 (str "Alle Anlagen im Postleitzahlenbereich " plz)]
      (unordered-list
        (for [id ids]
          (link-to (str "/details/" id) id))))))
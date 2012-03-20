(ns mbs-se-pv.views.maps
  (:require 
    [mbs-se-pv.views 
     [common :as common]]
    [mbs-db.core :as db])
  (:use [noir 
         [core :only (defpage defpartial url-for)]
         [options :only (resolve-url)]
         [response :only (redirect json)]]        
        [hiccup 
         [core :only (html)]
         [element :only (javascript-tag unordered-list link-to)]
         [page :only (include-css include-js)]]
        mbs-se-pv.views.util
        [clojure.string :only (join)]
        [org.clojars.smee.util :only (s2i)]))


(defpage "/data/powerdistribution.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        power (zipmap (keys groups) (map #(int (/ (apply + (map :anlagenkwp %)) 1000)) (vals groups)))] 
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

(defpage "/data/averageexpectedgain.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        fee (zipmap (keys groups) (for [g (vals groups) :let [n (count g), sum (apply + (map :sollyearkwp g))]] (int (/ sum n))))] 
    (json fee)))

(defpage "/data/invertercount.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        fee (zipmap (keys groups) (map #(apply + (map :anzahlwr %)) (vals groups)))] 
    (json fee)))

(defpage "/data/odd.json" {}
  (let [metadata (vals (db/get-metadata))
        plz (distinct (map :hppostleitzahl metadata))
        fee (zipmap plz (map #(if (odd? (s2i % 0)) 1 0) plz))] 
    (json fee)))

(defpage "/data/even.json" {}
  (let [metadata (vals (db/get-metadata))
        plz (distinct (map :hppostleitzahl metadata))
        fee (zipmap plz (map #(if (even? (s2i % 0)) 1 0) plz))] 
    (json fee)))

(defpage "/data/siemenscount.json" {}
  (let [metadata (vals (db/get-metadata))
        groups (group-by :hppostleitzahl metadata)
        in-str? (fn [s x] (and (not (nil? s)) (.contains (.toLowerCase s) x)))
        siemens (zipmap (keys groups) (for [g (vals groups) :let [wrs (map :hpwr g)]] (count (filter #(in-str? % "siemens") wrs))))] 
    (json siemens)))



(defn render-plz-map [div-id color-css-class json-link max-value]
  (let [base-url (or (noir.options/get :base-url) "")]
    (render-javascript-template "templates/choroplethplz.js" div-id color-css-class base-url json-link (int max-value))))

(defpartial map-includes []
  (include-css "/css/choroplethplz.css"
               "http://mbostock.github.com/d3/ex/colorbrewer.css")
  (include-js "http://mbostock.github.com/d3/d3.min.js"
              "http://mbostock.github.com/d3/d3.geo.min.js"))

(defpage maps "/maps" {}
  (common/layout 
     [:div.row-fluid
      [:div.span6 
       [:h3 "Installierte Leistung (in kW)"]
       [:div#chart]]
      [:div.span6
       [:h3 "Anzahl installierter PV-Anlagen"]
       [:div#chart2]]]
     [:div.row-fluid
      [:div.span6
       [:h3 "Durchschnittliche Einspeisevergütung (in cent)"]
       [:div#chart3]]
      [:div.span6
       [:h3 "Anzahl installierter Wechselrichter"]
       [:div#chart4]]] 
      (map-includes)
     (javascript-tag (render-plz-map "chart" "Reds" (resolve-url "/data/powerdistribution.json") 300))
     (javascript-tag (render-plz-map "chart2" "Blues" (resolve-url "/data/installationcounts.json") 10))
     (javascript-tag (render-plz-map "chart3" "Greens" (resolve-url "/data/averagefee.json") 50))
     (javascript-tag (render-plz-map "chart4" "Oranges" (resolve-url "/data/invertercount.json") 100))))

(defpage "/plz/:plz" {plz :plz}
  (let [ids (->> (db/get-metadata) vals (filter #(= plz (:hppostleitzahl %))) (map :id) sort)]
    (common/layout
      [:h3 (str "Alle Anlagen im Postleitzahlenbereich " plz)]
      (unordered-list
        (for [id ids]
          (link-to (str "/details/" id) id))))))
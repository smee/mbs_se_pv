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
         [form :only (drop-down)]
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

(defpartial render-maps [color-scheme]
  [:div.span6 
      [:div "Bitte doppelt auf eine Region klicken, um alle Anlagen darin zu sehen."]
      (drop-down {:onchange "mapfn(this.value)"}"mapDataSelector" 
                 [["Anzahl installierter PV-Anlagen" (resolve-url "/data/installationcounts.json")] 
                  ["Durchschnittliche Einspeisevergütung (cent)" (resolve-url "/data/averagefee.json")]
                  ["Anzahl installierter Wechselrichter" (resolve-url "/data/invertercount.json")]
                  ["Installierte Leistung (kW)" (resolve-url "/data/powerdistribution.json")]
                  ["Erwarter Ertrag (kWh/kWp)" (resolve-url "/data/averageexpectedgain.json")]
                  ["Anzahl von Siemenswechselrichtern" (resolve-url "/data/siemenscount.json")]
                  ["Anzahl von Siemenswechselrichtern" (resolve-url "/data/siemenscount.json")]
                  ["Gerade Postleitzahlen" (resolve-url "/data/even.json")]
                  ["Ungerade Postleitzahlen" (resolve-url "/data/odd.json")]]
                 (resolve-url "/data/powerdistribution.json"))
      [:div#map]
      (map-includes)
      ;; FIXME introduces a global variable 'mapfn' that holds an updater function for the map >:(
      (javascript-tag (str "mapfn="(render-plz-map "map" color-scheme (resolve-url "/data/powerdistribution.json") 82000)))])

(defpage maps "/maps" {}
  (common/layout
    (render-maps "RdBu")))

(defpage "/plz/:plz" {plz :plz}
  (let [ids (->> (db/get-metadata) vals (filter #(= plz (:hppostleitzahl %))) (map :id) sort)]
    (common/layout
      [:h3 (str "Alle Anlagen im Postleitzahlenbereich " plz)]
      (unordered-list
        (for [id ids]
          (link-to (str "/details/" id) id))))))


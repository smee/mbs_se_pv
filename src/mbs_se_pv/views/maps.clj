(ns mbs-se-pv.views.maps
  (:import java.util.Calendar)
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
        [org.clojars.smee 
         [time :only (as-calendar)]
         [util :only (s2i)]]))

(defn- by-year [year]
  (let [year-i (s2i year 2012)]
    (fn [entry]
      (if year 
        (= year-i (.get (as-calendar (get entry "Zeitpunkt der Inbetriebnahme")) Calendar/YEAR))
        true))))

(defpage "/data/powerdistribution.json" {:keys [year max]} 
  (let [metadata (filter (by-year year) e2)        
        groups (group-by #(get % "PLZ") metadata)
        power (zipmap (keys groups) (map #(int (apply + (map (fn [m] (get m "Installierte Leistung in kW")) %))) (vals groups)))
        power (if max (assoc power :max max) power)] 
    (json power)))

(defpage ins "/data/installationcounts.json" {:keys [year]}
  (let [metadata (filter (by-year year) e2)
        groups (group-by #(get % "PLZ") metadata)
        power (zipmap (keys groups) (map count (vals groups)))] 
    (json power)))



(defn render-plz-map [div-id color-css-class json-link max-value]
  (let [base-url (or (noir.options/get :base-url) "")]
    (render-javascript-template "templates/choroplethplz.js" div-id color-css-class base-url json-link (int max-value))))

(defpartial map-includes []
  (include-css "/css/choroplethplz.css" "/css/colorbrewer.css")
  (include-js "/js/chart/d3.v2.min.js"))

(defpartial render-maps [color-scheme]
  [:div.span12 
      [:div "Bitte doppelt auf eine Region klicken, um alle Anlagen darin zu sehen."]
      (drop-down {:onchange "mapfn(this.value)"}"mapDataSelector" 
                 (concat [["Anzahl installierter PV-Anlagen" (resolve-url "/data/installationcounts.json")]]
                         (for [i (range 1994 2011)] [(format "Anlagenzubau (Anzahl) (%d)" i) (resolve-url (format "/data/installationcounts.json?year=%d&max=500" i))])
                         (for [i (range 1994 2011)] [(format "Leistungszubau (kW) (%d)" i) (resolve-url (format "/data/powerdistribution.json?year=%d&max=200000" i))]))
                 (resolve-url "/data/powerdistribution.json"))
      [:div#map]
      (map-includes)
      ;; FIXME introduces a global variable 'mapfn' that holds an updater function for the map >:(
      (javascript-tag (str "mapfn="(render-plz-map "map" color-scheme (resolve-url "/data/powerdistribution.json") 82000)))])

(defpage maps "/maps" {}
  (common/layout
    (render-maps "RdBu")))

(defpage "/plz/:plz" {plz :plz}
  (let [ids (->> (db/get-metadata) vals (filter #(= plz ("PLZ" %))) (map :id) sort)]
    (common/layout
      [:h3 (str "Alle Anlagen im Postleitzahlenbereich " plz)]
      (unordered-list
        (for [id ids]
          (link-to (str "/details/" id) id))))))


(comment
  ; load and parse 50hz data
  (use 'clojure.java.io)
  (defn fix-plz [entries]
    (apply concat
           (for [[town entries] (group-by #(get % "Ort/Gemarkung") entries) 
                 :let [all-zipcodes (disj (set (distinct (map #(get % "PLZ") entries))) "0")
                       just-one-zipcode? (= 1 (count all-zipcodes))
                       common-zipcode (if just-one-zipcode? (first all-zipcodes))]]
             (for [e entries :let [plz (get e "PLZ")
                                   plz (if (= 4 (count plz)) (str "0" plz) plz)]]
               (if (and just-one-zipcode? (= "0" plz))
                 (assoc e "PLZ" common-zipcode)
                 (assoc e "PLZ" plz))))))
  (defn parse-entry [entry]
    (let [df (java.text.SimpleDateFormat. "dd.MM.yyyy")
          nf (java.text.NumberFormat/getNumberInstance java.util.Locale/GERMAN)]
      (-> entry 
        (update-in ["Zeitpunkt der Inbetriebnahme"] #(.parse df %))
        (update-in ["Installierte Leistung in kW"] #(.parse nf %)))))
  
  (with-open [r (reader "y:\\projekte\\2010_2014_eumonis\\echtdaten\\Anlagenstammdaten\\50Hertz_Anlagenstammdaten.csv" :encoding "Cp1252")]
    (let [[hdr & lines] (map #(.split % ";") (line-seq r))] 
      (def e2 (doall (map parse-entry (fix-plz (map #(zipmap hdr %) (remove #(every? empty? %) lines))))))))
  
  (count (filter #(= "0" (get % "PLZ")) e2))
  (use 'org.clojars.smee.serialization)
  (serialize "d:/Dropbox/Arbeit/Projekte/temp/50hz.clj" e2)
  )
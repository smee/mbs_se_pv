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
        [ring.util.codec :only [url-encode]]
        mbs-se-pv.views.util
        [org.clojars.smee.util :only (s2i)]))

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
    (common/include-js "/js/jquery.dataTables.min.js" "/js/dataTables.paging.bootstrap.js") 
    (javascript-tag (render-javascript-template "templates/render-datatable.js" "#names" (str (base-url) "/data/metadata.json")))))

(defpage "/" []
  (redirect (resolve-url (url-for start-page))))


(defn- search-for 
  "Filter for id substrings matching the search term"
  [search-term metadata]
  (filter #(or (.contains (-> % :address :name) search-term)
               (.contains (-> % :address :zipcode) search-term)
               (= :anzahlwr (s2i search-term -1))) metadata))

;; called via ajax, see http://datatables.net/usage/server-side for details
;; queries, filters, slices and dices pv metadata
(defpage "/data/metadata.json" {len :iDisplayLength
                           start :iDisplayStart
                           search-term :sSearch
                           echo :sEcho
                           sort-col :iSortCol_0
                           sort-dir :sSortDir_0
                           :as query}
  (let [len (s2i len 10)
        start (s2i start 1)
        col-id (s2i sort-col 0)
        get-id (comp :name :address)
        metadata (->> (db/get-metadata) vals (sort-by get-id))
        filtered (search-for search-term metadata)
        sort-key (case col-id 0 get-id, 1 :anlagenkwp, 2 :anzahlwr, 3 (comp :name :zipcode), get-id)
        sorted (sort-by sort-key filtered)
        sorted (if (= "desc" sort-dir) (reverse sorted) sorted)
        c (count sorted)
        spliced (subvec (vec sorted) (min start c) (min (+ len start) c))]
    (json 
      {:iTotalRecords (count metadata)
       :iTotalDisplayRecords (count filtered)
       :sEcho echo
       :aaData (into [] (map 
                          (juxt #(html (link-to (url-for ts/metadata-page {:id (url-encode (get-id %))}) (get-id %))) 
                                #(format "%.1f" (/ (:anlagenkwp %) 1000.0)) 
                                :anzahlwr
                                (comp :zipcode :address)) 
                          spliced))})))
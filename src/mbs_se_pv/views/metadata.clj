(ns mbs-se-pv.views.metadata
  (:require [mbs-db.core :as db]
            [mbs-se-pv.views.timeseries :as ts])
  (:use [noir.core :only (defpage url-for)]
        [noir.response :only (json)]
        [hiccup 
         [core :only (html)]
         [element :only (link-to)]]
        [ring.util.codec :only [url-encode]]
        [org.clojars.smee.util :only (s2i)]))

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


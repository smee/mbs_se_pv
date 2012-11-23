(ns mbs-se-pv.views.calendar
  (:use 
    [noir [core :only [defpage]]]
    [mbs-db.core :as db] 
    [org.clojars.smee [time :only [as-date as-calendar dates-seq]]])
  (:require [clojure [set :as set]
             [string :as string]]
            [mbs-se-pv.views [util :as util]])
  (:import java.util.Calendar))

;;;;;;;;;;; calender visualizations ;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- insert-missing-dates [template avail]
  (let [avail-dates (map (comp as-date :date) avail)
        all-dates (dates-seq (first avail-dates) (last avail-dates)) 
        missing (sort (set/difference (set all-dates) (set avail-dates)))
        missing (map #(assoc template :date %) missing)]
    (concat avail missing)))

(defpage missing-data "/data/:id/missing.csv" {plant :id}
  (->> plant
    (db/available-data)
    (map #(update-in % [:date] as-date))
    (insert-missing-dates {:num 0})
    (sort-by :date) 
    (map (fn [{:keys [date num]}] (str (.format (util/dateformat) date) "," num)))
    (concat ["date,num"])
    (string/join "\n")))

(defn- date-only [date]
  (as-date (doto (as-calendar date)
             (.set Calendar/HOUR_OF_DAY 12)
             (.set Calendar/MINUTE 0)
             (.set Calendar/SECOND 0))))

(defpage maintainance-dates "/data/:id/maintainance-dates.csv" {plant :id}
  (->> plant
    (db/adhoc "select * from maintainance where plant=?" )
    (mapcat (juxt :start :end))
    (map #(hash-map :date % :num 1))
    (map #(update-in % [:date] date-only))
    (insert-missing-dates {:num 0})
    (#(do (def d %) %))
    (sort-by :date)
    (map (fn [{:keys [date num]}] (str (.format (util/dateformat) date) "," num)))
    (concat ["date,num"])
    (string/join "\n")))
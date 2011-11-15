(ns mbs-se-pv.views.welcome
  (:require [mbs-se-pv.views [common :as common]
             [charts :as ch]]
            [mbs-se-pv.models.db :as db])
  (:use [noir.core :exclude (url-for url-for*)]
        hiccup.core
        hiccup.page-helpers
        [org.clojars.smee.util :only (per-thread-singleton)]
        [mbs-se-pv.views.util :only (escape-dots de-escape-dots)])
  (:import java.util.Calendar))

(def ^:private timeformat (per-thread-singleton #(java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")))
(def ^:private dateformat (per-thread-singleton #(java.text.SimpleDateFormat. "dd.MM.yyyy")))
(def ^:private dateformatrev (per-thread-singleton #(java.text.SimpleDateFormat. "yyyyMMdd")))

(defpage "/" []
         (common/layout
           [:p "Welcome to mbs-se-pv"]
           (unordered-list (map #(link-to (str "/series-of/" %) %) (db/all-names-limit 1 100)))))

(defn- start-of-day [millis]
  (- millis (mod millis (* 24 60 60 1000))))

(defn dates-seq [start-date end-date] 
  (let [cal (doto (Calendar/getInstance)
              (.setTimeInMillis start-date);
              (.add Calendar/DAY_OF_MONTH 1))
        date (.getTimeInMillis cal)]
    (when (< date end-date) 
      (lazy-seq
        (cons date (dates-seq date end-date))))))


(defpartial chart-link [id name start-time]
  (let [t (start-of-day start-time)] 
    (format 
      "/series-of/%s/single/%s/%s-%s/chart.png"  
      id
      name
      (.format (dateformatrev) t) (.format (dateformatrev) (+ t (* 24 60 60 1000))))))


(defpage stats "/series-of/:id/single/*/charts" {:keys [id *]}
  (let [name *
        min-time (db/min-time-of name)
        max-time (db/max-time-of name)] 
    (common/layout
      [:p (format "%s has %d values spanning %s till %s." 
                  name 
                  (db/count-all-values-of name) 
                  (.format (dateformat) min-time) 
                  (.format (dateformat) max-time))]
      (ordered-list 
        (map #(link-to (chart-link id name %) (.format (dateformat) %)) 
             (dates-seq min-time max-time))))))

(defpage "/series-of/:id" {arg :id}
  (let [q (str arg "%")
        c (db/count-all-series-of q)] 
    (common/layout
      [:p (format "%s has %d time series." arg c)
       (unordered-list 
         (for [n (sort (db/all-series-names-of q)) :let [title (.replace n \. \space)]]
           (link-to (format "/series-of/%s/single/%s/charts" arg n) title)))])))

(defpage wildcard "/foo/*/bar" {args :* :as p}
  [:p args])

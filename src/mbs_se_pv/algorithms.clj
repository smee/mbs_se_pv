(ns mbs-se-pv.algorithms
  (:require [mbs-db.core :as db]
            [mbs-se-pv.views.util :refer [ONE-DAY]]
            [org.clojars.smee.time :refer (as-calendar)]
            [stats.entropy :as e]
            [sun :as sun]))

(defn- day-of-year [{t :timestamp}]
    (.get (as-calendar t) java.util.Calendar/DAY_OF_YEAR))

(defn calculate-entropies [name id s e n bins min-hist max-hist denominator]
  (let [e (if (= s e) (+ e ONE-DAY) e)
        insol-name (or denominator (if (re-matches #"INVU1.*" name) "INVU1/MMET1.HorInsol.mag.f" "INVU2/MMET1.HorInsol.mag.f"))
        [x entropies] (db/ratios-in-time-range id name insol-name s e
             (fn [vs] 
               (let [days (partition-by day-of-year vs)
                     x-and-hists (map (juxt (comp :timestamp first) e/day2histogram) days)
                     x (drop n (mapv first x-and-hists))
                     hists (mapv second x-and-hists)
                     entropies (e/calculate-segmented-relative-entropies hists 
                                                                         :n n
                                                                         :bins bins
                                                                         :min-hist min-hist 
                                                                         :max-hist max-hist)]
                [x (vec entropies)])))
        
;        max-len (apply max (map count vs))
;        vs (remove #(< (count %) (* 0.95 max-len)) vs) ;TODO use expected number of data points per day, not maximum 
         ]
    {:x x 
     :entropies entropies;(sax/normalize entropies) 
     :name name 
     :denominator insol-name
     :n n
     :bins bins
     :min-hist min-hist 
     :max-hist max-hist}))

(defn simulate-insolation-values 
  "Create simulated insolation values for given geographical coordinates."
  [s e tracker? & opts]
  (let [opts (if (map? (first opts)) (first opts) (apply hash-map opts))
        {:keys [latitude longitude height delta-gmt module-tilt module-azimuth] 
         :or {latitude 12, longitude 50, height 0, delta-gmt 2, module-tilt 45, module-azimuth 180}} opts
        s (as-calendar s)
        start-day (-> s (.get java.util.Calendar/DAY_OF_YEAR))
        end-day (-> e as-calendar (.get java.util.Calendar/DAY_OF_YEAR))
        h-m (for [hour (range 0 24), minute (range 0 60 10)] [hour minute])]
    (remove #(Double/isNaN (:value %))
            (for [day-of-year (range start-day end-day), [h m] h-m
                  :let [{:keys [sunrise sunset] :as pos} (sun/sun-pos day-of-year h m latitude longitude height delta-gmt)
                        time (doto ^java.util.Calendar s
                               (.set java.util.Calendar/DAY_OF_YEAR day-of-year)
                               (.set java.util.Calendar/HOUR_OF_DAY h)
                               (.set java.util.Calendar/MINUTE m))
                        sunrise (doto ^java.util.Calendar (.clone time) 
                                  (.set java.util.Calendar/HOUR_OF_DAY (unchecked-divide-int sunrise 60))
                                  (.set java.util.Calendar/MINUTE (mod sunrise 60)))
                        sunset (doto ^java.util.Calendar (.clone time) 
                                  (.set java.util.Calendar/HOUR_OF_DAY (unchecked-divide-int sunset 60))
                                  (.set java.util.Calendar/MINUTE (mod sunset 60)))]
                  :when (and (.after time sunrise)(.before time sunset))]
              {:timestamp (.getTimeInMillis time)
               :value (max 0 (get (sun/module-sun-intensity pos height module-tilt module-azimuth) (if tracker? :s-incident :s-module)))}))))
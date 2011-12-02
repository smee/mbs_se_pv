(ns mbs-se-pv.models.db
  (:require [clojure.java.jdbc :as sql]))

(def ^:dynamic *db* 
  {:classname   "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :user        "root"
   :password     ""
   :subname      "//localhost:5029/solarlog"})

(defn- adhoc [query & params]
  (sql/with-connection *db*
       (sql/with-query-results res (apply vector query params) (doall (for [r res] r)))))

(defmacro defquery [name doc-string query res & body]
  `(defn ~name ~doc-string[& params#] 
     (sql/with-connection 
       *db* 
       (sql/with-query-results ~res (reduce conj [~query] params#) ~@body))))

(defn- fix-time
  ([r] (fix-time r :time))
  ([r & keys]
  (reduce #(if-let [ts (get % %2)] 
             (assoc % %2 (.getTime ts))
             (assoc % %2 0)) 
          r keys)))

(defquery count-all-values "Count all values" 
  "select count(*) as num from ts2"
  res
  (:num (first res)))

(defquery all-names-limit "Select all pv names (first part of the time series' names)."
  "select distinct SUBSTRING_INDEX(name,'.',1) as name from tsnames limit ?,?"
  res
  (doall (map :name res)))

(defquery count-all-series "Count all available time series."
  "select count(*) as num from tsnames;"
  res
  (:num (first res)))

(defquery count-all-series-of "Count all time series where the name is like the given parameter"
  "select count(name) as num from tsnames where name like ?"
  res
  (:num (first res)))

(defquery all-series-names-of "Select all time series names that are like the given parameter"
  "select * from tsnames where name like ?  order by name"
  res
  (doall (map :name res)))

(defquery count-all-values-of "Count all time series data points where the name is like the given parameter"
  "select count(*) as num from ts2 where belongs=(select belongs from tsnames where name=?)"
  res
  (:num (first res)))

(defquery all-values-of "Select all time series data points of a given name."
  "select time, value from ts2 where belongs=(select belongs from tsnames where name=?)  order by time"
  res
  (doall (map fix-time res)))

(defquery all-values-in-time-range "Select all time series data points of a given name that are between two times."
  "select time, value from ts2 where belongs=(select belongs from tsnames where name=?) and time >? and time <?  order by time"
  res
  (doall (map fix-time res)))

(defquery min-max-time-of "Select time of the oldest data point of a time series."
  "select min(time) as min, max(time) as max from ts2 where belongs=(select belongs from tsnames where name=?)"
  res
  (fix-time (first res) :min :max))

(defquery summed-values-in-time-range "Select times and added values of all time series that match a given parameter and are between two times."
  #_"select time,value,tsnames.name 
     from ts2, tsnames 
    where ts2.belongs=tsnames.belongs 
      and ts2.belongs in 
          (select belongs from tsnames where name like ?)
      and time >? and time <?;"
  "select time, sum(value) as value 
     from ts2 where belongs in (select belongs from tsnames where name like ?)  
      and time>? and time<? 
 group by time 
 order by time"
  res
  (doall (doall (map (comp #(assoc % :value (.doubleValue (:value %))) fix-time) res))))

(comment
  
  (binding [*db* *db*]
    (let [df (java.text.SimpleDateFormat. "yyyy-MM-dd")
          start (.. df (parse "2011-01-01") getTime)
          end (.. df (parse "2011-12-31") getTime)] 
      #_(num-series db)
      #_(all-names 0 10)
      #_(time (def x (all-values-of "1468.wr.0.pdc.string.0")))
      #_(time (def x (all-values-in-time-range "1468.wr.0.pdc.string.0" (java.sql.Timestamp. start) (java.sql.Timestamp. end))))
      (count-all-series-of "1555"))
    )
  
  )
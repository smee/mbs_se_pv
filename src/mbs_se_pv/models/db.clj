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

(defmacro defquery [name query res & body]
  `(defn ~name [& params#] 
     (sql/with-connection 
       *db* 
       (sql/with-query-results ~res (reduce conj [~query] params#) ~@body))))

(defquery count-all-values 
  "select count(*) as num from ts2"
  res
  (:num (first res)))

(defquery all-names-limit 
  "select distinct SUBSTRING_INDEX(name,'.',1) as name from tsnames limit ?,?"
  res
  (doall (map :name res)))

(defquery count-all-series
  "select count(*) as num from tsnames;"
  res
  (:num (first res)))

(defquery count-all-series-of
  "select count(name) as num from tsnames where name like ?"
  res
  (:num (first res)))

(defquery all-series-names-of
  "select * from tsnames where name like ?"
  res
  (doall (map :name res)))

(defquery count-all-values-of
  "select count(*) as num from ts2 where belongs=(select belongs from tsnames where name=?) order by time"
  res
  (:num (first res)))

(defquery all-values-of
  "select time, value from ts2 where belongs=(select belongs from tsnames where name=?) order by time"
  res
  (doall (for [r res] (assoc r :time (.getTime (:time r))))))

(defquery all-values-in-time-range
  "select time, value from ts2 where belongs=(select belongs from tsnames where name=?) and time >? and time <? order by time"
  res
  (doall (for [r res] (assoc r :time (.getTime (:time r))))))

(defquery min-time-of
  "select min(time) as time from ts2 where belongs=(select belongs from tsnames where name=?)"
  res
  (.getTime (:time (first res))))

(defquery max-time-of
  "select max(time) as time from ts2 where belongs=(select belongs from tsnames where name=?)"
  res
  (.getTime (:time (first res))))
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
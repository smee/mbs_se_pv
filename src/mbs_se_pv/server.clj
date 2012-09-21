(ns mbs-se-pv.server
  (:require 
    [noir.server :as server]
    [mbs-db.core :as db]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common charts timeseries welcome metadata maps])
  ;(:gen-class)
  )

;; initialize database settings
(let [url  (get (System/getenv) "DB-URL" "localhost:5029/psm")
      user (get (System/getenv) "DB-USER" "root")
      pw (get (System/getenv) "DB-PW" "")] 
  (db/use-db-settings {:classname   "com.mysql.jdbc.Driver"
                       :subprotocol "mysql"
                       :user         user
                       :password     pw
                       :subname      (str "//" url)}))

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'mbs-se-pv
                        ;:base-url "/eumonis-mbs-se-pv"
                        })))


;;;;;;;;;;;;;;; production settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def handler (server/gen-handler {:mode :prod
                  :ns 'mbs-se-pv
                  :base-url "/eumonis-mbs-se-pv-psm"}))

(comment
  (-main)
)
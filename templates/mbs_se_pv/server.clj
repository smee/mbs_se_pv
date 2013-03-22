(ns mbs-se-pv.server
  (:require 
    [noir.server :as server]
    [mbs-db.core :as db]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common calendar charts data timeseries welcome metadata #_maps])
  ;(:gen-class)
  )

;; initialize database settings
  (when (not *compile-files*)
    (db/use-db-settings {:classname   "com.mysql.jdbc.Driver"
                         :subprotocol "mysql"
                         :user         "{{db-user}}"
                         :password     "{{db-password}}"
                         :subname      "//{{db-url}}"
                         :connection-name "{{db-name}}"}))

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'mbs-se-pv
                        :base-url "{{base-url}}" 
                        })))


;;;;;;;;;;;;;;; production settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def handler (server/gen-handler {:mode :prod
                  :ns 'mbs-se-pv
                  :base-url "{{base-url}}"}))

(comment
  (-main)
)
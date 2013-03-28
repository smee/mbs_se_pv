(ns mbs-se-pv.server
  (:require 
    [noir.server :as server]
    ring.middleware.json
    [mbs-db.core :as db]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common calendar charts data timeseries welcome metadata])
  ;(:gen-class)
  )


;; initialize database settings
  (when (not *compile-files*)
    (do
      (server/add-middleware ring.middleware.json/wrap-json-params)
      (db/use-db-settings {:classname   "com.mysql.jdbc.Driver"
                           :subprotocol "mysql"
                           :user         "{{db-user}}"
                           :password     "{{db-password}}"
                           :subname      "//{{db-url}}"
                           :connection-name "{{db-name}}"})))


;;;;;;;;;;;;;;; production settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def handler (server/gen-handler {:mode :prod
                  :ns 'mbs-se-pv
                  :base-url "{{base-url}}"}))

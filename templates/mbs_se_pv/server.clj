(ns mbs-se-pv.server
  (:require 
    [noir.server :as server]
    ring.middleware.json
    [mbs-db.core :as db]
    [taoensso.tower.ring :refer [wrap-tower-middleware]] 
    [ring.middleware.gzip :refer [wrap-gzip]]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common calendar charts data analysis timeseries welcome])
  ;(:gen-class)
  )


;; initialize database settings
  (when (not *compile-files*)
    (do
      (server/add-middleware ring.middleware.json/wrap-json-params)
      (server/add-middleware wrap-tower-middleware {:tconfig {:dev-mode? false
                                                              :fallback-locale :en
                                                              :dictionary ; Map or named resource containing map
                                                              "translations.clj"}})
      (server/add-middleware wrap-gzip)
      (db/use-db-settings (merge db/mysql-config-psm
                                 {:classname   "com.mysql.jdbc.Driver"
                                  :user         "{{db-user}}"
                                  :password     "{{db-password}}"
                                  :subname      "//{{db-url}}"
                                  :connection-name "{{db-name}}"}))))


;;;;;;;;;;;;;;; production settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def handler (server/gen-handler {:mode :prod
                  :ns 'mbs-se-pv
                  :base-url "{{base-url}}"}))

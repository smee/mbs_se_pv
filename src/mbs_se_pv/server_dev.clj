(ns mbs-se-pv.server-dev
  (:require 
    [noir.server :as server]
    ring.middleware.json
    [mbs-db.core :as db]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common calendar charts data analysis timeseries welcome])
  ;(:gen-class)
  )

(defn full-head-avoidance
  [header-buffer-size jetty]
  (.setAttribute jetty "org.eclipse.jetty.server.Request.maxFormContentSize" 65536)
  (doseq [connector (.getConnectors jetty)]
    (.setRequestHeaderSize connector header-buffer-size)))

;; initialize database settings
(when (not *compile-files*)
  (do
    (server/add-middleware ring.middleware.json/wrap-json-params)
    (let [url  (get (System/getenv) "DB-URL" "localhost:5029/psm")
          user (get (System/getenv) "DB-USER" "root")
          pw (get (System/getenv) "DB-PW" "")
          name (get (System/getenv) "DB-NAME" "default")] 
      (db/use-db-settings {:classname   "com.mysql.jdbc.Driver"
                           :subprotocol "mysql"
                           :user         user
                           :password     pw
                           :subname      (str "//" url)
                           :connection-name name}))))

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'mbs-se-pv
                        ; increase max. size of requests to 64kb
                        :jetty-options 
                        {:configurator (partial full-head-avoidance 65536)} 
                        ;:base-url "/eumonis-mbs-se-pv"
                        })))


;;;;;;;;;;;;;;; production settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def handler (server/gen-handler {:mode :prod
                  :ns 'mbs-se-pv
                  :base-url ""}))

(comment
  (-main)
)
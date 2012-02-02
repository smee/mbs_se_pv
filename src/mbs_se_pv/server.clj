(ns mbs-se-pv.server
  (:require 
    [noir.server :as server]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common charts timeseries welcome metadata reports maps]
    [mbs-se-pv.middleware :as m])
  (:gen-class))

(server/add-middleware m/wrap-db-url)
(server/add-middleware m/wrap-encryption-key)

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
                  :base-url "/eumonis-mbs-se-pv"}))

(comment
  (-main)
)
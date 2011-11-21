(ns mbs-se-pv.server
  (:require 
    [noir.server :as server]
    ;; view namespaces need to be required explicitely for tomcat
    [mbs-se-pv.views common welcome charts])
  (:gen-class))

(server/load-views "src/mbs_se_pv/views/")

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'mbs-se-pv
                        ;:base-url "/eumonis-mbs-se-pv"
                        })))

(def handler (server/gen-handler {:mode :dev
                                  :ns 'mbs-se-pv
                                  :base-url "/eumonis-mbs-se-pv"}))
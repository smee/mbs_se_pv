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


;;;;;;;;;;;;;;; production settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- wrap-db-url 
  "Set database url from system proeprty 'DB-URL'."
  [handler]
  (let [url  (get (System/getenv) "DB-URL" "localhost:5029/solarlog")
        db-settings {:classname   "com.mysql.jdbc.Driver"
                     :subprotocol "mysql"
                     :user        "root"
                     :password     ""
                     :subname      (str "//" url)}]
    (fn [req]
      (binding [mbs-se-pv.models.db/*db* db-settings]
        (handler req)))))

(def handler (wrap-db-url
               (server/gen-handler {:mode :prod
                                    :ns 'mbs-se-pv
                                    :base-url "/eumonis-mbs-se-pv"})))
(ns mbs-se-pv.middleware
  (:require mbs-se-pv.views.util
            mbs-db.core))

(defn wrap-db-url 
  "Set database url from system proeprty 'DB-URL'."
  [handler]
  (let [url  (get (System/getenv) "DB-URL" "localhost:5029/psm")
        db-settings {:classname   "com.mysql.jdbc.Driver"
                     :subprotocol "mysql"
                     :user        "root"
                     :password     ""
                     :subname      (str "//" url)}]
    (fn [req]
      (binding [mbs-db.core/*db* db-settings]
        (handler req)))))



(ns mbs-se-pv.middleware
  (:require mbs-se-pv.views.util
            mbs-se-pv.models.db))

(defn wrap-db-url 
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

(defn wrap-encryption-key 
  "Encrypt pv names via vignere chiffre using the encryption key string
in the environment variable 'MBS-KEY'."
  [handler]
  (let [key  (get (System/getenv) "MBS-KEY" "A")]
    (fn [req]
      (binding [mbs-se-pv.views.util/*crypt-key* key]
        (handler req)))))

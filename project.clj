(defproject mbs-se-pv "0.1.0-SNAPSHOT"
            :description "Webanwendung der Machbarkeitsstudie Siemens-PV"
            :plugins [[lein-ring "0.7.5"]
                      [lein-resource "0.3.1"]]
            :hooks [leiningen.resource]
            :dependencies [[org.clojure/clojure "1.5.1"]
                           [noir "1.3.0-beta10"]
                           [ring/ring-json "0.2.0"]
                           [org.clojars.smee/common "1.2.7-SNAPSHOT"]
                           [mbs-db "1.2.0-SNAPSHOT"]
                           [solar-datamining "1.0.0-SNAPSHOT"]
                           [org.clojure/math.combinatorics "0.0.3"]
                           [chart-utils "1.0.2-SNAPSHOT"]
                           [incanter/incanter-core "1.5.4"]
                           [incanter/incanter-charts "1.5.4"
                            :exclusions [incanter/jfreechart]]
                           ;[clj-pdf "0.9.9-SNAPSHOT"] 
                           [org.clojure/tools.logging "0.2.6"]
                           [com.taoensso/tower "2.0.0-beta5"] ; i18n
                           ]
            :main mbs-se-pv.server
            :ring {:handler mbs-se-pv.server/handler}
            :resource {
                       :resource-paths ["templates"]
                       :target-path "target/classes"
                       :includes [ #".*" ]
                       :excludes [ #".*~" ]
                       ; um die Datenbankoptionen zu aendern
                       ; empfiehlt sich, beim Bauen des Warfiles die entsprechenden
                       ; Systemproperties zu setzen
                       :extra-values {:db-url      ~(get (System/getenv) "DBURL" "localhost:5029/psm")
                                      :db-user     ~(get (System/getenv) "DBUSER" "root")
                                      :db-password ~(get (System/getenv) "DBPW" "")
                                      :db-name     ~(get (System/getenv) "DBNAME" "default")
                                      :base-url    ~(get (System/getenv) "BASE_URL" "/eumonis-mbs-se-pv-psm")}})


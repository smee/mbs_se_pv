(defproject mbs-se-pv "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dev-dependencies [[lein-ring "0.4.6"]] 
            :dependencies [[org.clojure/clojure "1.3.0"]
                           [org.clojure/data.json "0.1.2"]
                           [noir "1.2.2-SNAPSHOT"]
                           [clj-cache "0.0.4"]
                           ;; explicitely require newest ring, bug in ring-0.3.x (accesses resource directories as files)
                           [ring/ring-core "1.0.0-beta2"]
                           [org.clojure/java.jdbc "0.1.0"]
                           [mysql/mysql-connector-java "5.1.17"]
                           [org.clojars.smee/common "1.2.0-SNAPSHOT"]
                           [import-pv-data "1.0.0-SNAPSHOT"]
                           [incanter "1.3.0-SNAPSHOT" 
                            :exclusions 
                            [swank-clojure swingrepl
                             incanter/incanter-excel 
                             incanter/incanter-latex 
                             incanter/incanter-pdf 
                             incanter/incanter-mongodb 
                             incanter/incanter-processing
                             jline]]]
            :main mbs-se-pv.server
            :ring {:handler mbs-se-pv.server/handler})


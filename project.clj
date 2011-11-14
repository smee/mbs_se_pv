(defproject mbs-se-pv "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dependencies [[org.clojure/clojure "1.3.0"]
                           [noir "1.2.1"]
                           [org.clojure/java.jdbc "0.1.0"]
                           [mysql/mysql-connector-java "5.1.17"]
                           [org.clojars.smee/common "1.1.0-SNAPSHOT"]
                           [incanter "1.3.0-SNAPSHOT" 
                            :exclusions 
                            [swank-clojure swingrepl
                             incanter/incanter-excel 
                             incanter/incanter-latex 
                             incanter/incanter-pdf 
                             incanter/incanter-mongodb 
                             incanter/incanter-processing
                             jline]]]
            :main mbs-se-pv.server)


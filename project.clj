(defproject mbs-se-pv "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dev-dependencies [[lein-ring "0.7.5"]] 
            :dependencies [[org.clojure/clojure "1.4.0"]
                           [noir "1.3.0-beta10"]
                           [org.clojars.smee/common "1.2.5"]
                           [mbs-db "1.1.0-SNAPSHOT"]
                           [solar-datamining "1.0.0-SNAPSHOT"]
                           [chart-utils "1.0.1"]
                           [mysql/mysql-connector-java "5.1.17"]
                           [incanter "1.3.0" 
                            :exclusions 
                            [swank-clojure swingrepl
                             incanter/incanter-excel 
                             incanter/incanter-latex 
                             incanter/incanter-pdf 
                             incanter/incanter-mongodb 
                             incanter/incanter-processing
                             jline]]
                           [de.uol.birt.api/maven-birt-integration "1.0.0-SNAPSHOT"]]
            :main mbs-se-pv.server
            :ring {:handler mbs-se-pv.server/handler})


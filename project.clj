(defproject mbs-se-pv "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dev-dependencies [[lein-ring "0.4.6"]] 
            :dependencies [[org.clojure/clojure "1.3.0"]
                           [noir "1.2.2-SNAPSHOT"]
                           [org.clojars.smee/common "1.2.0-SNAPSHOT"]
                           [mbs-db "1.0.0-SNAPSHOT"]
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


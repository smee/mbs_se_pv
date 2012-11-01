(defproject mbs-se-pv "0.1.0-SNAPSHOT"
            :description "Webanwendung der Machbarkeitsstudie Siemens-PV"
            :plugins [[lein-ring "0.7.5"]]
            :dependencies [[org.clojure/clojure "1.4.0"]
                           [noir "1.3.0-beta10"]
                           [org.clojars.smee/common "1.2.7-SNAPSHOT"]
                           [mbs-db "1.1.0-SNAPSHOT"]
                           [solar-datamining "1.0.0-SNAPSHOT"]
                           [chart-utils "1.0.2-SNAPSHOT"]
                           [incanter/incanter-core "1.3.0"]
                           [incanter/incanter-charts "1.3.0"]
                           ;[clj-pdf "0.9.9-SNAPSHOT"] 
                           ]
            :main mbs-se-pv.server
            :ring {:handler mbs-se-pv.server/handler})


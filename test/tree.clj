(ns tree
  (:use clojure.test
        mbs-se-pv.views.solarlog-names))

(deftest solarlog-names
  (let [names ["foo.wr.0.pac" "foo.wr.1.pac" "foo.wr.0.udc.string.0" "foo.wr.0.udc.string.1" "foo.wr.1.udc.string.0"]] 
    (is (= (restore-wr-hierarchy names) 
           {"Daten"
            {"nach Bauteil"
             {["Wechselrichter" [:sub "1"]]
              {["String" [:sub "0"]] {"Spannung DC" ["foo.wr.1.udc.string.0"]},
               "Leistung AC" ["foo.wr.1.pac"]},
              ["Wechselrichter" [:sub "0"]]
              {["String" [:sub "1"]] {"Spannung DC" ["foo.wr.0.udc.string.1"]},
               ["String" [:sub "0"]] {"Spannung DC" ["foo.wr.0.udc.string.0"]},
               "Leistung AC" ["foo.wr.0.pac"]}}}}))
    (is (= (cluster-by-type names)
           {"nach Datentyp"
            {"Spannung DC"
             {["Wechselrichter" [:sub "1"]]
              {["String" [:sub "0"]] ["foo.wr.1.udc.string.0"]},
              ["Wechselrichter" [:sub "0"]]
              {["String" [:sub "1"]] ["foo.wr.0.udc.string.1"],
               ["String" [:sub "0"]] ["foo.wr.0.udc.string.0"]}},
             "Leistung AC"
             {["Wechselrichter" [:sub "1"]] ["foo.wr.1.pac"],
              ["Wechselrichter" [:sub "0"]] ["foo.wr.0.pac"]}}}))))
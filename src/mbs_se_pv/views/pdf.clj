(ns mbs-se-pv.views.pdf
  (:use [noir 
         core
         [response :only (content-type json)]])
  (require [clj-pdf.core :as p]))

(defpage "/test.pdf" []
  (let [baos (java.io.ByteArrayOutputStream.)] 
    (p/pdf 
     [{:title  "Test doc"
     :size          :a4
     :orientation   "landscape"
     :subject "Some subject"
     :author "John Doe"
     :creator "Jane Doe"
     :doc-header ["inspired by" "William Shakespeare"]
     :header "page header"
     :footer "page"
     }

    [:table {:header [{:color [100 100 100]} "FOO"] :cellSpacing 20} 
     ["foo" 
      [:cell [:phrase {:style :italic :size 18 :family :halvetica :color [200 55 221]} "Hello Clojure!"]] 
       "baz"] 
     ["foo1" [:cell {:color [100 10 200]} "bar1"] "baz1"] 
     ["foo2" "bar2" [:cell [:table ["Inner table Col1" "Inner table Col2" "Inner table Col3"]]]]]     

    [:chapter "First Chapter"]

    [:anchor {:style {:size 15} :leading 20} "some anchor"]

    [:anchor [:phrase {:style :bold} "some anchor phrase"]]

    [:anchor "plain anchor"]        

    [:chunk {:style :bold} "small chunk of text"]

    [:phrase "some text here"]

    [:phrase {:style :italic :size 18 :family :helvetica :color [0 255 221]} "Hello Clojure!"]

    [:phrase [:chunk {:style :strikethru} "chunk one"] [:chunk {:size 20} "Big text"] "some other text"]

    [:paragraph "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam consequat malesuada commodo. Donec ac interdum velit. Duis metus velit, accumsan sed elementum ac, pharetra ut libero. Nam eleifend cursus fermentum. Mauris condimentum dignissim pellentesque. Sed at quam eget eros cursus iaculis ac sit amet odio. Curabitur feugiat nunc ut nisi posuere lacinia. Praesent pellentesque quam vel ipsum aliquet suscipit. Nullam nec nisi nec libero facilisis egestas. Curabitur quis velit augue, eu fringilla sem. Aliquam vitae metus ut libero laoreet tincidunt et id risus. Fusce consectetur bibendum sem, sit amet rutrum tortor sagittis id. Morbi imperdiet cursus mauris, ac ultricies ipsum hendrerit sit amet. Nulla sit amet justo elit, id iaculis metus. In eleifend porta massa, vitae tristique elit venenatis sit amet."]

    [:paragraph {:indent 50} [:phrase {:style :bold :size 18 :family :halvetica :color [0 255 221]} "Hello Clojure!"]]

    [:chapter [:paragraph "Second Chapter"]]

    [:paragraph {:keep-together true :indent 20} "a fine paragraph"]

    [:list {:roman true} [:chunk {:style :bold} "a bold item"] "another item" "yet another item"]

    [:chapter "Charts"]            
    [:chart {:type :bar-chart :title "Bar Chart" :x-label "Items" :y-label "Quality"} [2 "Foo"] [4 "Bar"] [10 "Baz"]]

    [:chart {:type :line-chart :title "Line Chart" :x-label "checkpoints" :y-label "units"} 
     ["Foo" [1 10] [2 13] [3 120] [4 455] [5 300] [6 600]]
     ["Bar" [1 13] [2 33] [3 320] [4 155] [5 200] [6 300]]]            
     [:chart {:type :pie-chart :title "Big Pie"} ["One" 21] ["Two" 23] ["Three" 345]]

    [:chart {:type :line-chart :time-series true :title "Time Chart" :x-label "time" :y-label "progress"}
     ["Incidents"
      ["2011-01-03-11:20:11" 200] 
      ["2011-01-03-11:25:11" 400] 
      ["2011-01-03-11:35:11" 350] 
      ["2011-01-03-12:20:11" 600]]]]
    baos)
  (content-type "pdf" (java.io.ByteArrayInputStream. (.toByteArray baos)))))

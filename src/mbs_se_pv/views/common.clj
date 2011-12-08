(ns mbs-se-pv.views.common
  (:use noir.core
        hiccup.core
        hiccup.page-helpers))

(defpartial eumonis-header []
  [:head 
   [:title "MBS_SE_PV"]
   (include-css "http://twitter.github.com/bootstrap/1.4.0/bootstrap.min.css"
                "/css/datepicker.css"
                "/css/dynatree/ui.dynatree.css")
   (include-js "http://ajax.googleapis.com/ajax/libs/jquery/1.7.0/jquery.min.js"
               "https://ajax.googleapis.com/ajax/libs/jqueryui/1.8.16/jquery-ui.min.js"
               "/js/datepicker.js"
               "/js/jquery.cookie.js"
               "/js/jquery.dynatree.min.js"
               "/js/bootstrap-modal.js")
   
   [:style {:type "text/css" :rel "stylesheet"}
    (format
      "body { 
         padding-top: 60px; 
       }
     .loading {
          background: url(%s) no-repeat center center;
      }" (url "/img/ajax-loader.gif"))]
   [:link {:rel "shortcut icon" :href (url "/img/favicon.ico")}]])

(defpartial eumonis-topbar [links active-idx]
  [:div.topbar
   [:div.fill
    [:div.container
     [:a.brand {:href "http://rz.eumonis.org"} "EUMONIS"]
     [:ul.nav
      (map-indexed #(if (= % active-idx) 
                      [:li.active %2] 
                      [:li %2]) 
                   links)]]]])

(defpartial eumonis-footer []
  [:footer
   [:div.row
    [:div.span4 [:p "&#169; EUMONIS-Konsortium 2001"]]
    [:div.span9 [:p "Das Projekt \"EUMONIS\" wird gef&#246;rdert durch das
			Bundesministerium f&#252;r Bildung und Forschung (BMBF) -
			F&#246;rderkennzeichen 01IS10033, Laufzeit 01.07.2010 bis
			30.06.2014."]]
    [:div.span3
     (link-to "http://www.bmbf.de/" 
              [:img {:src (url "/img/bmbf-ohne-rand.gif") 
                     :alt "gef&#246;rdert durch das Bundesministerium f&#252;r Bildung und Forschung"
                     :width "150px"}])]]])


(defpartial layout-with-links [topbar-links active-idx & contents]
  (html5
    (eumonis-header)
    [:body
     (eumonis-topbar topbar-links active-idx)
     [:div.container
      [:div.page-header ;{:style "padding: 40px;"}
       (link-to "/" 
                [:h1 "EUMONIS-Lab " 
                 [:small "Visualisierung von PV-Betriebsdaten"]])]
      contents
      (eumonis-footer)]]))

(defpartial layout [& contents]
  (apply layout-with-links [[:a {:href "#"} "Home"] [:a {:href "#contact"} "Kontakt"]] 0 contents))
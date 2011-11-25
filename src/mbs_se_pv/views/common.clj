(ns mbs-se-pv.views.common
  (:use noir.core
        hiccup.core
        hiccup.page-helpers))

(defpartial eumonis-header []
  [:head 
   [:title "MBS_SE_PV"]
   (include-css "/css/bootstrap.css"
                "/css/datepicker.css"
                "/css/dynatree/ui.dynatree.css")
   (include-js "http://ajax.googleapis.com/ajax/libs/jquery/1.7.0/jquery.min.js"
               "https://ajax.googleapis.com/ajax/libs/jqueryui/1.8.16/jquery-ui.min.js"
               "/js/datepicker.js"
               "/js/jquery.cookie.js"
               "/js/jquery.dynatree.min.js"
               "/js/bootstrap-modal.js")
   
   [:style {:type "text/css" :rel "stylesheet"}
    "body { 
         padding-top: 60px; 
       }
     .loading {
          background: url(/img/ajax-loader.gif) no-repeat center center;
      }"]
   [:link {:rel "shortcut icon" :href (url "/img/favicon.ico")}]])

(defpartial eumonis-topbar []
  [:div.topbar
   [:div.fill
    [:div.container
     [:a.brand {:href "http://rz.eumonis.org"} "EUMONIS"]
     [:ul.nav
      [:li.active [:a {:href "#"} "Home"]]
      [:li [:a {:href "#contact"} "Kontakt"]]]]]])

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

(defpartial layout [& content]
  (html5
    (eumonis-header)
    [:body
     (eumonis-topbar)
     [:div.container
      [:div.hero-unit {:style "padding: 40px;"}
       [:h1 {:style "font-size: 50px;"} "Visualisierung von PV-Betriebsdaten"]
       [:p "EUMONIS-Lab"]]
      content
      (eumonis-footer)]]))


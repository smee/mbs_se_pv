(ns mbs-se-pv.views.common
  (:use noir.core
        [hiccup core
         [element]
         [page :only (html5 include-css include-js)]])
  (:require [mbs-se-pv.views.util :as util]))

(defpartial eumonis-header []
  [:head 
   [:title "MBS_SE_PV"]
   (include-css "/css/bootstrap.min.css"
                "/css/showLoading.css"
                "/css/customizations.css")
   (include-js "/js/jquery.min.js"
               "/js/ensure.js" 
               "/js/jquery.cookie.js"
               "/js/jquery.showLoading.min.js")
   
   [:link {:rel "shortcut icon" :href "img/favicon.ico"}]])

(defpartial eumonis-topbar [[active-idx & links]]
  [:div.navbar
   [:div.navbar-inner
    [:div.container
     [:a.brand {:href "http://labs.eumonis.org"} "EUMONIS-Lab"]
     [:ul.nav
      (map-indexed #(if (= % active-idx) 
                      [:li.active %2] 
                      [:li %2]) 
                   links)]]]])

(defpartial eumonis-footer []
  [:footer
   [:div.span2 [:p "&#169; EUMONIS-Konsortium 2012"]]
    [:div.span8 [:p "Das Projekt \"EUMONIS\" wird gef&#246;rdert durch das
			Bundesministerium f&#252;r Bildung und Forschung (BMBF) -
			F&#246;rderkennzeichen 01IS10033, Laufzeit 01.07.2010 bis
			30.06.2014."]]
    [:div.span2
     (link-to "http://www.bmbf.de/" 
              [:img {:src (str (util/base-url) "/img/bmbf-ohne-rand.gif") 
                     :alt "gef&#246;rdert durch das Bundesministerium f&#252;r Bildung und Forschung"
                     :width "150px"}])]])


(defn layout-with-links [topbar-links sidebar-contents & contents]
  (html5
    (eumonis-header)
    [:body
     (eumonis-topbar topbar-links)
     [:div.container-fluid
      [:div.row-fluid sidebar-contents
       contents
       ]
      (eumonis-footer)]]))

(defn layout [& contents]
  (apply layout-with-links [0 [:a {:href "#"} "Home"] [:a {:href "#contact"} "Kontakt"]] nil contents))
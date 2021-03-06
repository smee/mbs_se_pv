(ns mbs-se-pv.views.common
  (:use [hiccup core
         [element]
         [page :only (html5)]])
  (:require [noir.core :refer [defpartial]] 
            [mbs-se-pv.views.util :as util]
            [clojure.java.io :as io]
            [org.clojars.smee.util :refer [md5]]))


(defn- add-versions 
  "create javascript and css links with a timestamp query parameter
to fix caching (cache files but reload if there are any changes, resembled by new timestamp query)"
  [uris]
  (for [local-uri uris] 
    (if (.startsWith local-uri "/")
      (let [contents (-> (str "public" local-uri) io/resource slurp)
            hash (md5 (.getBytes contents))]
        (str local-uri "?v=" hash))
      local-uri)))

(defpartial include-js [& uris]
  (apply hiccup.page/include-js (add-versions uris)))

(defpartial include-css [& uris]
  (apply hiccup.page/include-css (add-versions uris)))

(alter-var-root #'include-js memoize)
(alter-var-root #'include-css memoize)

(defpartial include-js-with-fallback 
  "Include a javascript file from a content delivery network (CDN). Tests if `dom-element` is available after loading the url.
If not, it loads a copy of the js file from a local uri."
  [url dom-element local-path]
  (list 
    (include-js url)
    (javascript-tag (format "%s || document.write('<script src=\"%s\"><\\/script>')" dom-element local-path))))

(defpartial eumonis-header []
  [:head 
   [:title "MBS_SE_PV"]
   (include-css "/css/bootstrap.min.css"
                "/css/showLoading.css"
                "/css/customizations.css")
   (include-js-with-fallback "http://ajax.googleapis.com/ajax/libs/jquery/1.9.0/jquery.min.js" "window.jQuery" "/js/jquery.min.js")
   (include-js "/js/jquery.cookie.js"
               "/js/jquery.blockUI.js"
               "/js/common.js")
   
   [:link {:rel "shortcut icon" :href "img/favicon.ico"}]])

(defpartial eumonis-topbar [[active-idx & links]]
  [:div.navbar
   [:div.navbar-inner
    [:div.container
     [:a.brand {:href "http://lab.eumonis.org"} "EUMONIS-Lab"]
     [:ul.nav
      (map-indexed #(if (= % active-idx) 
                      [:li.active %2] 
                      [:li %2]) 
                   links)]]]])

(defpartial eumonis-footer []
  [:footer
   [:div.span2 [:p "&#169; EUMONIS-Konsortium 2014"]]
    [:div.span8 [:p (util/t ::footer)]]
    [:div.span2
     (link-to "http://www.bmbf.de/" 
              [:img {:src (str (util/base-url) "/img/bmbf-ohne-rand.gif") 
                     :alt (util/t ::footer-alt) 
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
  (apply layout-with-links [0 [:a {:href "#"} (util/t ::home)] [:a {:href "#contact"} (util/t ::contact)]] nil contents))
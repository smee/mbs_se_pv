(ns mbs-se-pv.views.common
  (:use noir.core
        hiccup.core
        hiccup.page-helpers))

(defpartial layout [& content]
            (html5
              [:head
               [:title "mbs-se-pv"]
               (include-css "/css/reset.css")
               (include-css "/css/ui-lightness/jquery-ui-1.8.16.custom.css")
               (include-js "/js/jquery-1.6.2.min.js")
               (include-js "/js/jquery-ui-1.8.16.custom.min.js")]
              [:body
               [:div#wrapper
                content]]))

(ns mbs-se-pv.views.reports
  (:require 
    [clojure.java.io :as io]
    [mbs-se-pv.views.common :as common]
    [mbs-db.core :as db]
    [incanter.core :as ic]
    [incanter.charts :as ch])
  (:use  
    [clojure.string :only (split)]
    [noir.core :only (defpage)]
    [noir.response :only (content-type)]
    [mbs-se-pv.views.util :only (dateformatrev dateformat ONE-DAY)]
    [org.clojars.smee.util :only (s2i)])
  (:import
    org.eclipse.birt.report.engine.api.IRunAndRenderTask
    de.uol.birt.inverter.api.Framework
    de.uol.birt.inverter.api.OutputProfile
    de.uol.birt.inverter.api.Parameter))

(defonce birt-framework (Framework.))

(defpage "/report/:id/:year/:month" {:keys [id year month]}
  (let [tempfile (java.io.File/createTempFile "mbs-se-pv", "pdf")
        url (format "jdbc:%s:%s" (:subprotocol db/*db*) (:subname db/*db*))] 
    (with-open [template (.openStream (io/resource "reports/inverter_monthly_report.rptdesign"))
                t (.openReport birt-framework template)]
      (doto birt-framework        
        (.applyParameter t Parameter/JDBC_DRIVER (:classname db/*db*))
        (.applyParameter t Parameter/URL url)
        (.applyParameter t Parameter/USER (:user db/*db*))
        (.applyParameter t Parameter/PASSWORD (:password db/*db*))
        (.applyParameter t Parameter/PLANT id)
        (.applyParameter t Parameter/YEAR (s2i year))
        (.applyParameter t Parameter/MONTH (s2i month))
        (.renderReport t OutputProfile/PDF (.getAbsolutePath tempfile)))
      (content-type "application/pdf" tempfile))))


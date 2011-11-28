(ns mbs-se-pv.views.util
  (:use [org.clojars.smee.util :only (per-thread-singleton)]))

(def timeformat (per-thread-singleton #(java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss")))
(def dateformat (per-thread-singleton #(java.text.SimpleDateFormat. "dd.MM.yyyy")))
(def dateformatrev (per-thread-singleton #(java.text.SimpleDateFormat. "yyyyMMdd")))

(def ^:const ONE-DAY (* 24 60 60 1000))

(defn escape-dots [^String s]
  (.replace s \. \'))

(defn de-escape-dots [^String s]
  (.replace s \' \.))

(defn add-path [h path] 
  (let [dir (butlast path) 
        entry (last path)] 
    (update-in h dir (fn [x] (if x (conj x entry) [entry]))))) 

(defn restore-hierarchy [paths] 
  (reduce add-path {} paths))

;;;;;;;;;;;;;;;;;;;;;;;;; simple Vigenère chiffre ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def ^:dynamic *crypt-key* "A")
(defn- crypt-char [offset base c]
  (-> (int c)
    (+ offset)
    (- base)
    (mod 26)
    (+ base)
    char))

(defn- crypt [offsets s]
  (let [a (int \a)
        z (int \z)
        A (int \A)
        Z (int \Z)]
    (apply str (map (fn [offset c]
                      (cond
                        (<= a (int c) z) (crypt-char offset a c)
                        (<= A (int c) Z) (crypt-char offset A c)
                        :else c))
                    offsets s))))
(defn- get-offsets [key]
  (cycle (map #(- (int (Character/toUpperCase %)) (int \A)) key)))

(defn encrypt [s]
  (crypt (get-offsets *crypt-key*) s))

(defn decrypt [s]
  (crypt (map #(- 26 %) (get-offsets *crypt-key*)) s))

(defn encrypt-name [^String s]
  (let [idx (.indexOf s ".")
        name (subs s 0 idx)
        rest (subs s idx)]
    (str (encrypt name) rest)))
(defn decrypt-name [^String s]
  (let [idx (.indexOf s ".")
        name (subs s 0 idx)
        rest (subs s idx)]
    (str (decrypt name) rest)))


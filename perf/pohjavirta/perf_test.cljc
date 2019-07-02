(ns pohjavirta.perf-test
  (:require [criterium.core :as cc]
            [pohjavirta.server :as server])
  (:import (io.undertow.util HttpString)
           (java.util Iterator Map$Entry)))

(defn response-pef []

  ;; 9ns
  (cc/quick-bench
    (let [r (server/->Response 200 {"Content-Type" "text/plain"} "hello world 2.0")]
      [(:status r) (:headers r) (:body r)]))

  ;; 11ns
  (cc/quick-bench
    (let [r (server/->SimpleResponse 200 "text/plain" "hello world 2.0")]
      [(:status r) (:headers r) (:body r)]))

  ;; 20ns
  (cc/quick-bench
    (let [r {:status 200
             :headers {"Content-Type" "text/plain"}
             :body "hello 4.0"}]
      [(:status r) (:headers r) (:body r)])))

(defn http-string-perf []

  ;; 20ns
  (cc/quick-bench
    (HttpString. "Content-Type"))

  ;; 5ns
  (cc/quick-bench
    (HttpString/tryFromString "Content-Type")))

(defn reducing-perf []

  ;;34ns
  (cc/quick-bench
    (let [headers {"Content-Type" "text/plain"}
          m (java.util.HashMap.)
          i ^Iterator (.iterator ^Iterable headers)]
      (loop []
        (if (.hasNext i)
          (let [e ^Map$Entry (.next i)]
            (.put m (HttpString/tryFromString ^String (.getKey e)) ^String (.getValue e))
            (recur))))
      m))

  ;; 60ns
  (cc/quick-bench
    (let [headers {"Content-Type" "text/plain"}
          m (java.util.HashMap.)]
      (reduce-kv
        (fn [acc k v]
          (.put ^java.util.HashMap acc (HttpString/tryFromString ^String k) ^String v)
          m)
        m
        headers))))

(comment
  (response-pef)
  (http-string-perf)
  (reducing-perf))

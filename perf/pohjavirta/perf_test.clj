(ns pohjavirta.perf-test
  (:require [criterium.core :as cc]
            [pohjavirta.response :as response]
            [pohjavirta.ring :as ring]
            [pohjavirta.request :as request])
  (:import (io.undertow.util HttpString)
           (java.util Iterator Map$Entry)
           (io.undertow.server HttpServerExchange)))

(defn response-pef []

  ;; 9ns
  (cc/quick-bench
    (let [r (response/->Response 200 {"Content-Type" "text/plain"} "hello world 2.0")]
      [(:status r) (:headers r) (:body r)]))

  ;; 11ns
  #_(cc/quick-bench
    (let [r (response/->SimpleResponse 200 "text/plain" "hello world 2.0")]
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

(declare EXC)

(defmacro b! [& body]
  `(do
     (println ~@body)
     (cc/quick-bench ~@body)))

(defn request-mapping-test []
  (let [ex ^HttpServerExchange EXC
        r (request/create ex)]

    ;; 70ns
    (b! (ring/get-server-port r))

    ;; 28ns
    (b! (ring/get-server-name r))

    ;; 83ns
    (b! (ring/get-remote-addr r))

    ;; 7ns
    (b! (ring/get-uri r))

    ;; 29ns
    (b! (ring/get-query-string r))

    ;; 19ns
    (b! (ring/get-scheme r))

    ;; 63ns -> 17ns
    (b! (ring/get-request-method r))

    ;; 8ns
    (b! (ring/get-protocol r))

    ;; 2000ns -> 500ns -> 430ns
    (b! (ring/get-headers r))

    ;; 8ns
    (b! (ring/get-body r))

    ;; 8ns
    (b! (ring/get-context r))))

(comment
  (response-pef)
  (http-string-perf)
  (reducing-perf))

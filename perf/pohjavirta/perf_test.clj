(ns pohjavirta.perf-test
  (:require [criterium.core :as cc]
            [pohjavirta.server :as server]
            [pohjavirta.ring :as ring])
  (:import (io.undertow.util HttpString)
           (java.util Iterator Map$Entry)
           (io.undertow.server HttpServerExchange)))

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

(declare EXC)

(defmacro b! [& body]
  `(do
     (println ~@body)
     (cc/quick-bench ~@body)))

(defn request-mapping-test []
  (let [ex ^HttpServerExchange EXC
        r (server/->ZeroCopyRequest ex)]

    ;; 70ns
    (b! (ring/server-port r))

    ;; 28ns
    (b! (ring/server-name r))

    ;; 83ns
    (b! (ring/remote-addr r))

    ;; 7ns
    (b! (ring/uri r))

    ;; 29ns
    (b! (ring/query-string r))

    ;; 19ns
    (b! (ring/scheme r))

    ;; 63ns -> 17ns
    (b! (ring/request-method r))

    ;; 8ns
    (b! (ring/protocol r))

    ;; 2000ns -> 500ns -> 430ns
    (b! (ring/headers r))

    ;; 8ns
    (b! (ring/body r))

    ;; 8ns
    (b! (ring/context r))))

(comment
  (response-pef)
  (http-string-perf)
  (reducing-perf))

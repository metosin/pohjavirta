(ns pohjavirta.server-test
  (:require [clojure.test :refer :all]
            [pohjavirta.server :as server]
            [pohjavirta.response :as response]
            [pohjavirta.async :as a]
            [hikari-cp.core :as hikari])
  (:import (java.nio ByteBuffer)
           (java.util.concurrent CompletableFuture)
           (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util Headers SameThreadExecutor)
           (java.util.concurrent ThreadLocalRandom)
           (java.util.function Function)))

(def http-handler
  (let [bytes (.getBytes "Hello, World!")
        buffer (-> bytes count ByteBuffer/allocateDirect (.put bytes) .flip)]
    (reify HttpHandler
      (handleRequest [_ exchange]
        (-> ^HttpServerExchange exchange
            (.getResponseHeaders)
            (.put Headers/CONTENT_TYPE "text/plain"))
        (-> ^HttpServerExchange exchange
            (.getResponseSender)
            (.send (.duplicate ^ByteBuffer buffer)))))))

(defn handler [_]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "hello 4.0"})

(defn handler [_]
  (response/->Response 200 {"Content-Type" "text/plain"} "hello world 2.0"))

(defn handler [_]
  (response/->SimpleResponse 200 "text/plain" "Hello, World!"))

(defn handler [_]
  (let [f (CompletableFuture.)]
    (future (.complete f (response/->SimpleResponse 200 "text/plain" "Hello, Future!")))
    f))

(require '[promesa.core :as p])
(require '[porsas.async :as pa])
(require '[porsas.core :as ps])
(require '[jsonista.core :as j])

(def pool
  (pa/pool
    {:uri "postgresql://localhost:5432/hello_world"
     :user "benchmarkdbuser"
     :password "benchmarkdbpass"
     ;:pipelining-limit 4
     :size #_256 (* 2 (.availableProcessors (Runtime/getRuntime)))}))

(def pool2
  #_(hikari/make-datasource
      {:jdbc-url "jdbc:postgresql://localhost:5432/hello_world"
       :username "benchmarkdbuser"
       :password "benchmarkdbpass"
       :maximum-pool-size 256}))

(def mapper (pa/data-mapper {:row (pa/rs->compiled-record)}))

(def mapper2 (ps/data-mapper {:row (ps/rs->compiled-record)}))

(defn random []
  (unchecked-inc (.nextInt (ThreadLocalRandom/current) 10000)))

(defn handler [_]
  (let [world (with-open [con (ps/get-connection pool2)]
                (ps/query-one mapper2 con ["SELECT id, randomnumber from WORLD where id=?" (random)]))]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (j/write-value-as-bytes world)}))

(def http-handler
  (reify HttpHandler
    (handleRequest [_ exchange]
      #_(.startBlocking exchange)
      (.dispatch
        ^HttpServerExchange exchange
        ^Runnable (^:once fn* []
                    (let [world (with-open [con (ps/get-connection pool2)]
                                  (ps/query-one mapper2 con ["SELECT id, randomnumber from WORLD where id=?" (random)]))]
                      (response/send-response
                        {:status 200
                         :headers {"Content-Type" "application/json"}
                         :body (j/write-value-as-bytes world)}
                        exchange)))))))

(def http-handler
  (reify HttpHandler
    (handleRequest [_ exchange]
      #_(.startBlocking exchange)
      (.dispatch
        ^HttpServerExchange exchange
        ^Runnable (^:once fn* []
                    (-> (pa/query-one mapper pool ["SELECT id, randomnumber from WORLD where id=$1" (random)])
                        (p/then (fn [world]
                                  {:status 200
                                   :headers {"Content-Type" "application/json"}
                                   :body (j/write-value-as-bytes world)}))))))))

(defn handler [_]
  (-> (a/promise "Hello, Async?")
      (a/then (fn [response]
                {:status 200,
                 :headers {"Content-Type" "text/plain"}
                 :body response}))))

(defn handler [_]
  (let [cf (CompletableFuture.)]
    (.complete cf "Hello, Async?")
    (.thenApply cf (reify Function
                     (apply [_ response]
                       {:status 200,
                        :headers {"Content-Type" "text/plain"}
                        :body response})))))

(defn handler [_]
  (-> (p/promise "Hello, Async!")
      (p/then (fn [message]
                {:status 200,
                 :headers {"Content-Type" "text/plain"}
                 :body message}))))

(defn handler [_]
  (-> (pa/query-one mapper pool ["SELECT id, randomnumber from WORLD where id=$1" (random)])
      (.thenApply (reify Function
                    (apply [_ world]
                      {:status 200
                       :headers {"Content-Type" "application/json"}
                       :body (j/write-value-as-bytes world)})))))

(defn handler [_]
  (-> (pa/query-one mapper pool ["SELECT id, randomnumber from WORLD where id=$1" (random)])
      (a/then (fn [world]
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body (j/write-value-as-bytes world)}))))

(require '[porsas.async.cps :as cps])

(def mapper2 (cps/data-mapper {:row (cps/rs->compiled-record)}))

(def pool2
  (cps/pool
    {:uri "postgresql://localhost:5432/hello_world"
     :user "benchmarkdbuser"
     :password "benchmarkdbpass"
     ;:pipelining-limit 4
     :size (* 2 (.availableProcessors (Runtime/getRuntime)))}))

(def http-handler
  (reify HttpHandler
    (handleRequest [_ exchange]
      #_(println "1:" (Thread/currentThread))
      (.dispatch
        ^HttpServerExchange exchange
        SameThreadExecutor/INSTANCE
        ^Runnable (^:once fn* []
                    #_(println "2:" (Thread/currentThread))
                    (cps/query-one
                      mapper2
                      pool2
                      ["SELECT id, randomnumber from WORLD where id=$1" (random)]
                      (fn [response]
                        #_(println "3:" (Thread/currentThread))
                        (response/send-response
                          {:status 200
                           :headers {"Content-Type" "application/json"}
                           :body (j/write-value-as-bytes response)}
                          exchange)
                        (.endExchange exchange))
                      (fn [response]
                        (response/send-response
                          {:status 500
                           :headers {"Content-Type" "application/json"}
                           :body {:error response}}
                          exchange)
                        (.endExchange exchange))))))))

(comment
  (def server (server/create #'handler))
  (def server (server/create http-handler))
  (server/start server)
  (server/stop server))

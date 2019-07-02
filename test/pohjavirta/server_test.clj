(ns pohjavirta.server-test
  (:require [clojure.test :refer :all]
            [pohjavirta.server :as server])
  (:import (java.nio ByteBuffer)
           (java.util.concurrent CompletableFuture)
           (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util Headers)))

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
  (server/->Response 200 {"Content-Type" "text/plain"} "hello world 2.0"))

(defn handler [_]
  (server/->SimpleResponse 200 "text/plain" "Hello, World!"))

(defn handler [_]
  (let [f (CompletableFuture.)]
    (future (.complete f (server/->SimpleResponse 200 "text/plain" "Hello, World!")))
    f))

(comment
  (def server (server/create #'handler))
  (def server (server/create http-handler))
  (server/start server)
  (server/stop server))

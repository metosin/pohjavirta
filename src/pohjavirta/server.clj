(ns pohjavirta.server
  (:refer-clojure :exclude [constantly])
  (:require [pohjavirta.request :as request]
            [pohjavirta.response :as response])
  (:import (io.undertow Undertow UndertowOptions)
           (io.undertow.server HttpHandler)
           (io.undertow.server.handlers SetHeaderHandler)))

(set! *warn-on-reflection* true)

(def default-options
  {:port 8080
   :host "localhost"})

(defn create
  ([handler]
   (create handler nil))
  ([handler options]
   ;; server-options, socket-options, worker-options
   ;; :dispatch?, virtual-host, virtual-host
   ;; ::ssl-port :keystore, :key-password, :truststore :trust-password, :ssl-context, :key-managers, :trust-managers, :client-auth, :http2?
   (let [{:keys [port host buffer-size io-threads worker-threads direct-buffers dispatch ssl-port ssl-context]} (merge default-options options)
         handler (cond
                   (instance? HttpHandler handler) handler
                   (and (var? handler) (instance? HttpHandler @handler)) @handler
                   dispatch (reify HttpHandler
                              (handleRequest [_ exchange]
                                (.dispatch exchange
                                           ^Runnable (fn []
                                                       (.startBlocking exchange)
                                                       (let [request (request/create exchange)
                                                             response (handler request)]
                                                         (response/send-response response exchange))))))
                   :else (reify HttpHandler
                           (handleRequest [_ exchange]
                             (let [request (request/create exchange)
                                   response (handler request)]
                               (response/send-response response exchange)))))]
     (assert (not= port ssl-port))
     (-> (Undertow/builder)
         (.addHttpListener port host)
         (cond-> (and ssl-port ssl-context)
           (.addHttpsListener ssl-port host ssl-context))
         (cond-> buffer-size (.setBufferSize buffer-size))
         (cond-> io-threads (.setIoThreads io-threads))
         (cond-> worker-threads (.setWorkerThreads worker-threads))
         (cond-> direct-buffers (.setDirectBuffers direct-buffers))
         (.setServerOption UndertowOptions/ALWAYS_SET_KEEP_ALIVE, false)
         (.setServerOption UndertowOptions/BUFFER_PIPELINED_DATA, true)
         (.setHandler (SetHeaderHandler. ^HttpHandler handler "Server" "pohjavirta"))
         (.build)))))

(defn start [^Undertow server]
  (.start server))

(defn stop [^Undertow server]
  (.stop server))

(ns pohjavirta.server
  (:refer-clojure :exclude [constantly])
  (:require [pohjavirta.request :as request]
            [pohjavirta.response :as response])
  (:import (io.undertow Undertow UndertowOptions)
           (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.server.handlers SetHeaderHandler)
           (io.undertow.util HttpString)
           (pohjavirta.response ResponseSender)))

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
   (let [{:keys [port host buffer-size io-threads worker-threads direct-buffers]} (merge default-options options)
         handler (cond
                   (instance? HttpHandler handler) handler
                   (and (var? handler) (instance? HttpHandler @handler)) @handler
                   :else (reify HttpHandler
                           (handleRequest [_ exchange]
                             (let [request (request/create exchange)
                                   response (handler request)]
                               (response/send-response response exchange)))))]
     (-> (Undertow/builder)
         (.addHttpListener port host)
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

;;
;; helpers
;;

(defn dispatch [handler]
  (fn [request]
    (let [exchange ^HttpServerExchange (request/exchange request)]
      (if (.isInIoThread exchange)
        (.dispatch
          exchange
          ^Runnable
          (^:once fn* []
            (response/send-response (handler request) exchange)
            (.endExchange exchange)))
        (handler request)))))

(defn constantly
  ([handler]
   (constantly :ring handler))
  ([mode handler]
   (let [{:keys [status headers body]} (handler ::irrelevant)
         exchange (gensym)
         exchange' (gensym)
         headers-sym (gensym)
         body-sym (gensym)
         lets (atom [])
         code (cond-> []
                      (not (#{200 nil} status)) (conj `(.setStatusCode ~exchange ~status))
                      (seq headers) (conj
                                      `(let [~headers-sym (.getResponseHeaders ~exchange)]
                                         ~@(mapv
                                             (fn [[k v]]
                                               (let [k' (gensym)]
                                                 (swap! lets conj `[~k' (HttpString/tryFromString ~k)])
                                                 `(.put ~headers-sym ~k' ~v))) headers)))
                      body (conj (do
                                   (swap! lets conj `[~body-sym (response/direct-byte-buffer ~body)])
                                   `(.send (.getResponseSender ~exchange) (.duplicate ~body-sym)))))]
     (eval
       (case mode
         :raw `(let [~@(apply concat @lets)]
                 (reify HttpHandler
                   (handleRequest [_ ~exchange]
                     ~@(if (seq code) code))))
         :ring `(let [~@(apply concat @lets)]
                  (fn [~'_]
                    ~@(if (seq code)
                        `[(reify ResponseSender
                            ;; protocols don't support type hints
                            (send-response [_ ~exchange']
                              (let [~exchange ~(with-meta exchange' {:tag 'io.undertow.server.HttpServerExchange})]
                                ~@code)))]))))))))

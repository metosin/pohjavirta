(ns pohjavirta.exchange
  (:refer-clojure :exclude [constantly])
  (:require [pohjavirta.request :as request]
            [pohjavirta.response :as response])
  (:import (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util HttpString)
           (pohjavirta.response ResponseSender)))

(set! *warn-on-reflection* true)

(defn dispatch [handler]
  (fn [request]
    (let [exchange ^HttpServerExchange (request/exchange request)]
      (if (.isInIoThread exchange)
        (.dispatch
          exchange
          ^Runnable
          (^:once fn* []
            (let [result (handler request)]
              (response/send-response result exchange)
              (when-not (response/async? result)
                (.endExchange exchange)))))
        (handler request)))))

(defn constantly
  ([handler]
   (constantly :ring handler))
  ([mode handler]
   (let [{:keys [status headers body]} (handler ::irrelevant)
         exchange (gensym)
         headers-sym (gensym)
         body-sym (gensym)
         lets (atom [])
         code (cond-> []
                      (not (#{200 nil} status)) (conj `(.setStatusCode ~(with-meta exchange {:tag 'io.undertow.server.HttpServerExchange}) ~status))
                      (seq headers) (conj
                                      `(let [~headers-sym (.getResponseHeaders ~(with-meta exchange {:tag 'io.undertow.server.HttpServerExchange}))]
                                         ~@(mapv
                                             (fn [[k v]]
                                               (let [k' (gensym)]
                                                 (swap! lets conj `[~k' (HttpString/tryFromString ~k)])
                                                 `(.put ~headers-sym ~k' ~v))) headers)))
                      body (conj (do
                                   (swap! lets conj `[~body-sym (response/direct-byte-buffer ~body)])
                                   `(.send (.getResponseSender ~(with-meta exchange {:tag 'io.undertow.server.HttpServerExchange})) (.duplicate ~body-sym)))))]
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
                            (send-response [_ ~exchange]
                              ~@code))]))))))))

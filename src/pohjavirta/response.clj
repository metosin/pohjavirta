(ns pohjavirta.response
  (:import (io.undertow.server HttpServerExchange)
           (io.undertow.util Headers HeaderMap HttpString SameThreadExecutor)
           (java.nio ByteBuffer)
           (java.util.concurrent CompletableFuture)
           (java.util Iterator Map$Entry)
           (java.util.function Function)))

(set! *warn-on-reflection* true)

(defrecord Response [status -headers body])

(defrecord SimpleResponse [status content-type body])

(defprotocol ResponseSender
  (send-response [this exchange]))

(defprotocol BodySender
  (send-body [this exchange]))

(defn- -send-map-like-response [response ^HttpServerExchange exchange]
  (when-let [status (:status response)]
    (.setStatusCode ^HttpServerExchange exchange status))
  (when-let [-headers (:headers response)]
    (let [responseHeaders ^HeaderMap (.getResponseHeaders exchange)
          i ^Iterator (.iterator ^Iterable -headers)]
      (loop []
        (if (.hasNext i)
          (let [e ^Map$Entry (.next i)]
            (.put responseHeaders (HttpString/tryFromString ^String (.getKey e)) ^String (.getValue e))
            (recur))))))
  (send-body (:body response) exchange))

(extend-protocol ResponseSender

  CompletableFuture
  (send-response [response exchange]
    ;(println "1:" (Thread/currentThread))
    (.dispatch
      ^HttpServerExchange exchange
      SameThreadExecutor/INSTANCE
      ^Runnable (^:once fn* []
                  ;(println "2:" (Thread/currentThread))
                  (.thenApply
                    response
                    ^Function (reify Function
                                (apply [_ response]
                                  ;(println "3:" (Thread/currentThread))
                                  (send-response response exchange)
                                  (.endExchange ^HttpServerExchange exchange)))))))

  clojure.lang.PersistentArrayMap
  (send-response [response exchange]
    (-send-map-like-response response exchange))

  clojure.lang.PersistentHashMap
  (send-response [response exchange]
    (-send-map-like-response response exchange))

  Response
  (send-response [response exchange]
    (-send-map-like-response response exchange))

  SimpleResponse
  (send-response [response exchange]
    (def EXC exchange)
    (when-let [status (:status response)]
      (.setStatusCode ^HttpServerExchange exchange status))
    (when-let [content-type (:content-type response)]
      (-> ^HttpServerExchange exchange
          (.getResponseHeaders)
          (.put Headers/CONTENT_TYPE ^String content-type)))
    (send-body (:body response) exchange))

  Object
  (send-response [response _]
    (throw (UnsupportedOperationException. (str "Response class not supported: " (class response))))))

(extend-protocol BodySender

  (Class/forName "[B")
  (send-body [body exchange]
    (-> ^HttpServerExchange exchange
        (.getResponseSender)
        (.send (ByteBuffer/wrap body))))

  String
  (send-body [body exchange]
    (-> ^HttpServerExchange exchange
        (.getResponseSender)
        (.send ^String body)))

  ByteBuffer
  (send-body [body exchange]
    (-> ^HttpServerExchange exchange
        (.getResponseSender)
        (.send ^ByteBuffer body)))

  Object
  (send-body [body _]
    (throw (UnsupportedOperationException. (str "Body class not supported: " (class body)))))

  nil
  (send-body [_ _]))

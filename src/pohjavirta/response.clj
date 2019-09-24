(ns pohjavirta.response
  (:import (io.undertow.server HttpServerExchange)
           (io.undertow.util HeaderMap HttpString SameThreadExecutor)
           (java.nio ByteBuffer)
           (java.util.concurrent CompletionStage)
           (java.util Iterator Map$Entry)
           (java.util.function Function)))

(set! *warn-on-reflection* true)

(defrecord Response [status headers body])

(defprotocol ResponseSender
  (async? [this])
  (send-response [this exchange]))

(defprotocol BodySender
  (send-body [this exchange]))

(defn ^:no-doc ^ByteBuffer direct-byte-buffer [x]
  (cond
    (string? x) (recur (.getBytes ^String x "UTF-8"))
    (bytes? x) (.flip (.put (ByteBuffer/allocateDirect (alength ^bytes x)) ^bytes x))
    (instance? ByteBuffer x) x
    :else (throw (UnsupportedOperationException. (str "invalid type " (class x) ": " x)))))

(defn- -send-map-like-response [response ^HttpServerExchange exchange]
  (when-let [status (:status response)]
    (.setStatusCode ^HttpServerExchange exchange status))
  (when-let [headers (:headers response)]
    (let [responseHeaders ^HeaderMap (.getResponseHeaders exchange)
          i ^Iterator (.iterator ^Iterable headers)]
      (loop []
        (if (.hasNext i)
          (let [e ^Map$Entry (.next i)]
            (.put responseHeaders (HttpString/tryFromString ^String (.getKey e)) ^String (.getValue e))
            (recur))))))
  (send-body (:body response) exchange))

(extend-protocol ResponseSender

  HttpServerExchange
  (async? [_] false)
  (send-response [_ _])

  CompletionStage
  (async? [_] true)
  (send-response [response exchange]
    (.dispatch
      ^HttpServerExchange exchange
      SameThreadExecutor/INSTANCE
      ^Runnable (^:once fn* []
                  (.thenApply
                    response
                    ^Function (reify Function
                                (apply [_ response]
                                  (send-response response exchange)
                                  (.endExchange ^HttpServerExchange exchange)))))))

  clojure.lang.PersistentArrayMap
  (async? [_] false)
  (send-response [response exchange]
    (-send-map-like-response response exchange))

  clojure.lang.PersistentHashMap
  (async? [_] false)
  (send-response [response exchange]
    (-send-map-like-response response exchange))

  Response
  (async? [_] false)
  (send-response [response exchange]
    (-send-map-like-response response exchange))

  nil
  (async? [_] false)
  (send-response [_ exchange]
    (.endExchange ^HttpServerExchange exchange)))

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

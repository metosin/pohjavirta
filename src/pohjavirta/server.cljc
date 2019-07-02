(ns pohjavirta.server
  (:require [inline.potemkin.collections :as fpc]
            [clojure.string :as str])
  (:import (io.undertow Undertow UndertowOptions)
           (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util Headers HeaderMap HeaderValues HttpString SameThreadExecutor)
           (io.undertow.server.handlers SetHeaderHandler)
           (java.nio ByteBuffer)
           (java.util.concurrent CompletableFuture)
           (java.util Iterator Map$Entry)
           (java.util.function Function)))

(set! *warn-on-reflection* true)

;;
;; Request
;;

(defn- -headers [^HeaderMap header-map]
  (reduce
    (fn [acc ^HeaderValues hv]
      (assoc acc (-> hv .getHeaderName .toString .toLowerCase) (str/join "," hv)))
    {} header-map))

(fpc/def-derived-map ZeroCopyRequest [^HttpServerExchange exchange]
  :server-port (-> exchange .getDestinationAddress .getPort)
  :server-name (.getHostName exchange)
  :remote-addr (-> exchange .getSourceAddress .getAddress .getHostAddress)
  :uri (.getRequestURI exchange)
  :query-string (let [qs (.getQueryString exchange)] (if-not (= "" qs) qs))
  :scheme (-> exchange .getRequestScheme keyword)
  :request-method (-> exchange .getRequestMethod .toString .toLowerCase keyword)
  :protocol (-> exchange .getProtocol .toString)
  :headers (-> exchange .getRequestHeaders -headers)
  :body (when (.isBlocking exchange) (.getInputStream exchange))
  :context (.getResolvedPath exchange)
  :exchange exchange)

;;
;; Response
;;

(defrecord Response [status -headers body])

(defrecord SimpleResponse [status content-type body])

(defprotocol ResponseSender
  (-send-response [this exchange]))

(defprotocol BodySender
  (-send-body [this exchange]))

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
  (-send-body (:body response) exchange))

(extend-protocol ResponseSender

  CompletableFuture
  (-send-response [response exchange]
    (.dispatch
      ^HttpServerExchange exchange
      SameThreadExecutor/INSTANCE
      ^Runnable (^:once fn* []
                  (.thenApply
                    response
                    ^Function (reify Function
                                (apply [_ response]
                                  (-send-response response exchange)
                                  (.endExchange ^HttpServerExchange exchange)))))))

  clojure.lang.PersistentArrayMap
  (-send-response [response exchange]
    (-send-map-like-response response exchange))

  clojure.lang.PersistentHashMap
  (-send-response [response exchange]
    (-send-map-like-response response exchange))

  Response
  (-send-response [response exchange]
    (-send-map-like-response response exchange))

  SimpleResponse
  (-send-response [response exchange]
    (when-let [status (:status response)]
      (.setStatusCode ^HttpServerExchange exchange status))
    (when-let [content-type (:content-type response)]
      (-> ^HttpServerExchange exchange
          (.getResponseHeaders)
          (.put Headers/CONTENT_TYPE ^String content-type)))
    (-send-body (:body response) exchange)))

(extend-protocol BodySender

  (Class/forName "[B")
  (-send-body [body exchange]
    (-> ^HttpServerExchange exchange
        (.getResponseSender)
        (.send (ByteBuffer/wrap body))))

  String
  (-send-body [body exchange]
    (-> ^HttpServerExchange exchange
        (.getResponseSender)
        (.send ^String body)))

  ByteBuffer
  (-send-body [body exchange]
    (-> ^HttpServerExchange exchange
        (.getResponseSender)
        (.send ^ByteBuffer body)))

  Object
  (-send-body [body _]
    (throw (UnsupportedOperationException. (str "Body class not supported: " (class body)))))

  nil
  (-send-body [_ _]))

;;
;; public api
;;

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
         handler (if (instance? HttpHandler handler)
                   handler
                   (reify HttpHandler
                     (handleRequest [_ exchange]
                       (let [request (->ZeroCopyRequest exchange)
                             response (handler request)]
                         (-send-response response exchange)))))]
     (-> (Undertow/builder)
         (.addHttpListener port host)
         (cond-> buffer-size (.setBufferSize buffer-size))
         (cond-> io-threads (.setIoThreads io-threads))
         (cond-> worker-threads (.setWorkerThreads worker-threads))
         (cond-> direct-buffers (.setDirectBuffers direct-buffers))
         (.setServerOption UndertowOptions/ALWAYS_SET_KEEP_ALIVE, false)
         (.setHandler (SetHeaderHandler. ^HttpHandler handler "Server" "pohjavirta"))
         (.build)))))

(defn start [^Undertow server]
  (.start server))

(defn stop [^Undertow server]
  (.stop server))

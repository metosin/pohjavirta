(ns pohjavirta.request
  (:require [inline.potemkin.collections :as fpc]
            [clojure.string :as str]
            [pohjavirta.ring :as ring])
  (:import (java.util HashMap Collections Map)
           (io.undertow.util Methods HttpString HeaderValues HeaderMap Headers)
           (java.lang.reflect Field)
           (io.undertow.server HttpServerExchange)))

(set! *warn-on-reflection* true)

(def ^Map request-methods
  (let [methods (HashMap.)]
    (doseq [^String m (->> Methods
                           .getDeclaredFields
                           (filter #(= HttpString (.getType ^Field %)))
                           (map #(.getName ^Field %)))]
      (.put methods (HttpString. m) (-> m .toLowerCase keyword)))
    (Collections/unmodifiableMap methods)))

(defn ->method [^HttpString method-http-string]
  (or (.get request-methods method-http-string)
      (-> method-http-string .toString .toLowerCase)))

(def ^Map request-headers
  (let [headers (HashMap.)]
    (doseq [^String m (->> Headers
                           .getDeclaredFields
                           (filter #(= HttpString (.getType ^Field %)))
                           (map #(.getName ^Field %)))]
      (.put headers (HttpString. m) (.toLowerCase m)))
    (Collections/unmodifiableMap headers)))

(defn ->header [^HttpString header-http-string]
  (or (.get request-headers header-http-string)
      (-> header-http-string .toString .toLowerCase)))

(defn ->headers [^HeaderMap header-map]
  (let [it (.iterator header-map)]
    (loop [m {}]
      (if (.hasNext it)
        (let [hvs ^HeaderValues (.next it)
              hk (-> hvs .getHeaderName ->header)
              hv (if (= 1 (.size hvs)) (.getFirst hvs) (str/join "," hvs))]
          (recur (assoc m hk hv)))
        m))))

(fpc/def-derived-map ZeroCopyRequest [^HttpServerExchange exchange]
  :server-port (-> exchange .getDestinationAddress .getPort)
  :server-name (.getHostName exchange)
  :remote-addr (-> exchange .getSourceAddress .getAddress .getHostAddress)
  :uri (.getRequestURI exchange)
  :query-string (let [qs (.getQueryString exchange)] (if-not (= "" qs) qs))
  :scheme (-> exchange .getRequestScheme keyword)
  :request-method (-> exchange .getRequestMethod .toString .toLowerCase keyword)
  :protocol (-> exchange .getProtocol .toString)
  :headers (-> exchange .getRequestHeaders ->headers)
  :body (if (.isBlocking exchange) (.getInputStream exchange))
  :context (.getResolvedPath exchange))

(defprotocol Exchange
  (^HttpServerExchange exchange [this]))

(extend-protocol Exchange
  ZeroCopyRequest
  (exchange [this] (.exchange this)))

(extend-protocol ring/RingRequest
  ZeroCopyRequest
  (server-port [this] (-> this exchange .getDestinationAddress .getPort))
  (server-name [this] (-> this exchange .getHostName))
  (remote-addr [this] (-> this exchange .getSourceAddress .getAddress .getHostAddress))
  (uri [this] (let [e ^HttpServerExchange (.exchange this)] (-> e .getRequestURI)))
  (query-string [this] (let [qs (-> this exchange .getQueryString)] (if-not (= "" qs) qs)))
  (scheme [this] (-> this exchange .getRequestScheme keyword))
  (request-method [this] (->> this exchange .getRequestMethod ->method))
  (protocol [this] (-> this exchange .getProtocol .toString))
  (headers [this] (-> this exchange .getRequestHeaders ->headers))
  (body [this] (if (-> this exchange .isBlocking) (-> this exchange .getInputStream)))
  (context [this] (-> this exchange .getResolvedPath)))

(defmethod print-method ZeroCopyRequest [request ^java.io.Writer w]
  (let [exchange ^HttpServerExchange (:echange request)
        data (if exchange {:xnio (.isInIoThread exchange)
                           :blocking (.isBlocking exchange)} {})]
    (.write w (str "#ZeroCopyRequest" data))))

;;
;; Public api
;;

(defn create [^HttpServerExchange exchange]
  (->ZeroCopyRequest exchange))

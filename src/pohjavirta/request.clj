(ns pohjavirta.request
  (:require [inline.potemkin.collections :as fpc]
            [clojure.string :as str]
            [pohjavirta.ring :as ring])
  (:import (java.util HashMap Collections Map Map$Entry)
           (io.undertow.util HttpString HeaderValues HeaderMap Headers)
           (java.lang.reflect Field)
           (io.undertow.server HttpServerExchange)
           (clojure.lang MapEquivalence IPersistentMap Counted IPersistentCollection IPersistentVector ILookup IFn IObj Seqable Reversible SeqIterator Associative IHashEq MapEntry)))

(set! *warn-on-reflection* true)

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
  :query-string (let [qs (.getQueryString exchange)] (if-not (.equals "" qs) qs))
  :scheme (-> exchange .getRequestScheme keyword)
  :request-method (-> exchange .getRequestMethod .toString .toLowerCase keyword)
  :protocol (-> exchange .getProtocol .toString)
  :headers (-> exchange .getRequestHeaders ->headers)
  :body (if (.isBlocking exchange) (.getInputStream exchange))
  :context (.getResolvedPath exchange))

(defprotocol Exchange
  (^HttpServerExchange exchange [this]))

;;
;; PartialCopyRequest
;;

(deftype PartialCopyRequest [drec dmap]
  Exchange
  (exchange [_] (exchange dmap))

  ring/RingRequest
  (get-server-port [this] (:server-port this))
  (get-server-name [this] (:server-name this))
  (get-remote-addr [this] (:remote-addr this))
  (get-uri [_this] (:uri drec))
  (get-query-string [this] (:query-string this))
  (get-scheme [this] (:schema this))
  (get-request-method [_this] (:request-method drec))
  (get-protocol [this] (:protocol this))
  (get-headers [this] (:headers this))
  (get-header [this header] (get (:headers this) header))
  (get-body [this] (:body this))
  (get-context [this] (:context this))

  IPersistentMap
  (assoc [_ k v]
    (PartialCopyRequest. (assoc drec k v) dmap))
  (assocEx [this k v]
    (if (.containsKey this k)
      (throw (RuntimeException. "Key already present"))
      (assoc this k v)))
  (without [_ k]
    (PartialCopyRequest. (dissoc drec k) (dissoc dmap k)))

  MapEquivalence

  Map
  (get [this k]
    (.valAt this k))
  (isEmpty [this]
    (not (.seq this)))
  (entrySet [this]
    (set (or (.seq this) [])))
  (containsValue [this v]
    (boolean (seq (filter #(= % v) (.values this)))))
  (values [this]
    (map val (.seq this)))
  (size [this]
    (count (.seq this)))

  Counted

  IPersistentCollection
  (count [this]
    (.size this))
  (cons [this o]
    (condp instance? o
      Map$Entry (let [^Map$Entry e o]
                  (.assoc this (.getKey e) (.getValue e)))
      IPersistentVector (if (= 2 (count o))
                          (.assoc this (nth o 0) (nth o 1))
                          (throw (IllegalArgumentException. "Vector arg to map conj must be a pair")))
      (reduce
        (fn [^IPersistentMap m ^Map$Entry e]
          (.assoc m (.getKey e) (.getValue e)))
        this o)))
  (empty [_]
    (PartialCopyRequest. (empty drec) (empty dmap)))
  (equiv [this o]
    (and (instance? Map o)
         (= (.count this) (count o))
         (every? (fn [[k v :as kv]]
                   (= kv (find o k)))
                 (.seq this))))

  Seqable
  (seq [_]
    (seq (into {} (concat (seq dmap) (seq drec)))))

  Reversible
  (rseq [this]
    (reverse (seq this)))

  Iterable
  (iterator [this]
    (SeqIterator. (.seq this)))

  Associative
  (containsKey [_ k]
    (or (contains? drec k) (contains? dmap k)))
  (entryAt [this k]
    (when (.containsKey this k)
      (MapEntry. k (.valAt this k))))

  ILookup
  (valAt [_ k]
    (or (.valAt ^ILookup drec k nil) (.get ^Map dmap k)))
  (valAt [_ k not-found]
    (if-let [entry (or (find drec k) (find dmap k))]
      (val entry)
      not-found))

  IFn
  (invoke [this k]
    (.valAt this k))
  (invoke [this k not-found]
    (.valAt this k not-found))

  IObj
  (meta [_]
    (.meta ^IObj drec))
  (withMeta [_ m]
    (PartialCopyRequest. (.withMeta ^IObj drec m) dmap))

  IHashEq
  (hasheq [this] (.hasheq ^IHashEq (into {} this)))

  Object
  (toString [this]
    (str "{" (str/join ", " (for [[k v] this] (str k " " v))) "}"))
  (equals [this other]
    (.equiv this other))
  (hashCode [this]
    (.hashCode ^Object (into {} this))))

(defmethod print-method PartialCopyRequest [^PartialCopyRequest o ^java.io.Writer w]
  (.write w "#PartialCopyRequest")
  (.write w (pr-str (into {} (seq o)))))

(extend-protocol Exchange
  ZeroCopyRequest
  (exchange [this] (.exchange this)))

(defmethod print-method ZeroCopyRequest [request ^java.io.Writer w]
  (let [exchange ^HttpServerExchange (exchange request)
        data (if exchange {:xnio (.isInIoThread exchange)
                           :blocking (.isBlocking exchange)} {})]
    (.write w (str "#ZeroCopyRequest" data))))

(defrecord Request [uri request-method])

;;
;; public api
;;

(defn create
  "Creates a partial-copy request where the commonly needed
  keys are copied to an internal [[Request]] Record, while
  rest of the keys are handled via [[ZeroCopyRequest]]."
  [^HttpServerExchange exchange]
  (->PartialCopyRequest
    ;; eager copy
    (->Request
      (.getRequestURI exchange)
      (-> exchange .getRequestMethod .toString .toLowerCase keyword))
    ;; realize on access
    (->ZeroCopyRequest exchange)))

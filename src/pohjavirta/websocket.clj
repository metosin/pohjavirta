(ns pohjavirta.websocket
  (:import [io.undertow.websockets WebSocketConnectionCallback
                                   WebSocketProtocolHandshakeHandler]
           [io.undertow.websockets.core WebSocketChannel
                                        AbstractReceiveListener
                                        BufferedTextMessage]
           [io.undertow.server HttpHandler
                               HttpServerExchange]
           [io.undertow.websockets.spi WebSocketHttpExchange]))

;; this may fit better elsewhere. At first start here though to keep modular

(defn ws-listener [{:keys [on-receive on-close on-error]}]
  (proxy [AbstractReceiveListener] []
    (onFullTextMessage [^WebSocketChannel channel ^BufferedTextMessage message]
      (when on-receive
        (on-receive {:channel channel
                     :data (.getData message)})))
    (onClose [^WebSocketChannel ws-channel _]
      (when on-close
        (on-close {:channel ws-channel})))
    (onError [^WebSocketChannel channel ^Throwable error]
      (when on-error
        (on-error {:channel channel
                   :error   error})))))

(defn ws-callback
  [{:keys [on-connect] :as ws-opts}]
  (reify WebSocketConnectionCallback
    (^void onConnect [_ ^WebSocketHttpExchange exchange ^WebSocketChannel channel]
      (.set (.getReceiveSetter channel) (ws-listener ws-opts))
      (on-connect {:channel channel})
      (.resumeReceives channel))))

(defn ws-handler
  "Convenience function to create a basic websocket handler"
  [opts]
  (let [ws-handshake-handler (WebSocketProtocolHandshakeHandler. ^WebSocketConnectionCallback (ws-callback opts))]
    (reify HttpHandler
      (^void handleRequest [_ ^HttpServerExchange exchange]
        (.handleRequest ws-handshake-handler exchange)))))
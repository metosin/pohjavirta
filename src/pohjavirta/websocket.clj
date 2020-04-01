(ns pohjavirta.websocket
  (:import [io.undertow.websockets WebSocketConnectionCallback]
           [io.undertow.websockets.core WebSocketChannel
                                        AbstractReceiveListener
                                        BufferedTextMessage]
           [io.undertow.websockets.spi WebSocketHttpExchange]
           [io.undertow Handlers]
           [org.xnio ChannelListener]))

;; this may fit better elsewhere. At first start here though to keep modular

(defn ws-listener
  "Default listener"
  [{:keys [on-receive on-close on-error]
    :or   {on-receive (constantly nil)
           on-close   (constantly nil)
           on-error   (constantly nil)}}]
  (proxy [AbstractReceiveListener] []
    (onFullTextMessage [^WebSocketChannel channel ^BufferedTextMessage message]
      (on-receive {:channel channel
                   :data    (.getData message)}))
    (onClose [^WebSocketChannel ws-channel _]
      (on-close {:channel ws-channel}))
    (onError [^WebSocketChannel channel ^Throwable error]
      (on-error {:channel channel
                 :error   error}))))

(defn ws-callback
  [{:keys [on-connect listener] :as ws-opts}]
  (let [listener (if (instance? ChannelListener listener)
                   listener
                   (ws-listener ws-opts))]
    (reify WebSocketConnectionCallback
      (^void onConnect [_ ^WebSocketHttpExchange exchange ^WebSocketChannel channel]
        (.set (.getReceiveSetter channel) listener)
        (on-connect {:channel channel})
        (.resumeReceives channel)))))

(defn ws-handler
  "Convenience function to create a basic websocket handler"
  [opts]
  (Handlers/websocket (ws-callback opts)))
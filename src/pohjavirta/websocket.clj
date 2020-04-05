(ns pohjavirta.websocket
  (:import [io.undertow.websockets WebSocketConnectionCallback]
           [io.undertow.websockets.core AbstractReceiveListener
                                        BufferedBinaryMessage
                                        BufferedTextMessage
                                        CloseMessage
                                        WebSocketChannel]
           [io.undertow.websockets.spi WebSocketHttpExchange]
           [io.undertow Handlers]
           [org.xnio ChannelListener]
           [pohjavirta Util]))

;; this may fit better elsewhere. At first start here though to keep modular

(defn ws-listener
  "Default websocket listener

   Takes a map of functions as opts:
   :on-message | fn taking map of keys :channel, :data
   :on-close   | fn taking map of keys :channel, :message
   :on-error   | fn taking map of keys :channel, :error

   Each key defaults to no action"
  [{:keys [on-message on-close on-error]
    :or   {on-message (constantly nil)
           on-close   (constantly nil)
           on-error   (constantly nil)}}]
  (proxy [AbstractReceiveListener] []
    (onFullTextMessage [^WebSocketChannel channel ^BufferedTextMessage message]
      (on-message {:channel channel
                   :data    (.getData message)}))
    (onFullBinaryMessage [^WebSocketChannel channel ^BufferedBinaryMessage message]
      (let [pooled (.getData message)]
        (try
          (let [payload (.getResource pooled)]
            (on-message {:channel channel
                         :data (Util/toArray payload)}))
          (finally
            (.free pooled)))))
    (onCloseMessage [^CloseMessage message ^WebSocketChannel channel]
      (on-close {:channel channel
                 :message message}))
    (onError [^WebSocketChannel channel ^Throwable error]
      (on-error {:channel channel
                 :error   error}))))

(defn ws-callback
  [{:keys [on-open listener]
    :or   {on-open (constantly nil)}
    :as   ws-opts}]
  (let [listener (if (instance? ChannelListener listener)
                   listener
                   (ws-listener ws-opts))]
    (reify WebSocketConnectionCallback
      (^void onConnect [_ ^WebSocketHttpExchange exchange ^WebSocketChannel channel]
        (on-open {:channel channel})
        (.set (.getReceiveSetter channel) listener)
        (.resumeReceives channel)))))

(defn ws-handler
  "Convenience function to create a basic websocket handler"
  [opts]
  (Handlers/websocket (ws-callback opts)))
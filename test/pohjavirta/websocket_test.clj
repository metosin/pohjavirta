(ns pohjavirta.websocket-test
  (:require [clojure.test :refer :all]
            [pohjavirta.websocket :as ws]
            [gniazdo.core :as gniazdo]
            [pohjavirta.server :as server]))

(set! *warn-on-reflection* true)

(defn test-websocket
  []
  (let [events  (atom [])
        errors  (atom [])
        result  (promise)
        config  {:on-connect (fn [_]
                               (swap! events conj :open))
                 :on-receive (fn [{:keys [data]}]
                               (swap! events conj data))
                 :on-close   (fn [_]
                               (deliver result @events))
                 :on-error   (fn [{:keys [error]}]
                               (swap! errors conj error))}
        handler (ws/ws-handler config)
        server  (server/create handler)]
    (try
      (server/start server)
      (let [socket (gniazdo/connect "ws://localhost:8080/")]
        (gniazdo/send-msg socket "hello")
        (gniazdo/close socket))
      (deref result 2000 :fail)
      (finally
        (server/stop server)))))

(test-websocket)
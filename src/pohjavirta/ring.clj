(ns pohjavirta.ring)

(defprotocol RingRequest
  (get-server-port [this])
  (get-server-name [this])
  (get-remote-addr [this])
  (get-uri [this])
  (get-query-string [this])
  (get-scheme [this])
  (get-request-method [this])
  (get-protocol [this])
  (get-headers [this])
  (get-header [this header])
  (get-body [this])
  (get-context [this]))

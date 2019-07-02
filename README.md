# pohjavirta

Fast, low-fat & Non-blocking Clojure wrapper for the [UnderTow](http://undertow.io/) Web Server.

## 

```clj
(require '[pohjavirta.server :as server])

(defn handler [_]
  {:status 200,
   :headers {"Content-Type" "text/plain"}
   :body "Hello, World!"})

;; create and start the server
(-> #'handler server/create server/start)
```

By default, the server listens to `localhost` on port `8080`. Trying with [HTTPie](https://httpie.org/):

```bash
$ http :8080

HTTP/1.1 200 OK
Content-Length: 13
Content-Type: text/plain
Date: Tue, 02 Jul 2019 13:21:59 GMT
Server: pohjavirta

Hello, World!
```

Let's run some load with [wrk](https://github.com/wg/wrk):

```bash
$ wrk -t16 -c16 -d2s http://127.0.0.1:8080

Running 2s test @ http://127.0.0.1:8080
  16 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   125.96us   37.69us   1.61ms   94.95%
    Req/Sec     7.82k   530.24     8.55k    83.63%
  261483 requests in 2.10s, 32.42MB read

Requests/sec: 124548.75
Transfer/sec:     15.44MB
```

Async responses, using [promesa](http://funcool.github.io/promesa/latest/):

```clj
(require '[promesa.core :as p])

(defn handler [_]
  (-> (p/promise "Hello, Async!")
      (p/then (fn [message]
                {:status 200,
                 :headers {"Content-Type" "text/plain"}
                 :body message}))))
```

We redefined the handler, so no need to restart the server:

```bash
$ http :8080

HTTP/1.1 200 OK
Content-Length: 13
Content-Type: text/plain
Date: Tue, 02 Jul 2019 13:27:35 GMT
Server: pohjavirta

Hello, Async!
```

Performance is still good:

```bash
$ wrk -t16 -c16 -d2s http://127.0.0.1:8080

Running 2s test @ http://127.0.0.1:8080
  16 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   144.56us   50.60us   1.86ms   96.96%
    Req/Sec     6.86k   329.73     7.34k    76.79%
  229225 requests in 2.10s, 29.51MB read

Requests/sec: 109145.61
Transfer/sec:     14.05MB
```

## Status

WIP. 

## License

Copyright © 2019 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.

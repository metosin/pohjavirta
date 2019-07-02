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

Hello, World!
```

Let's run some load with [wrk](https://github.com/wg/wrk):

```bash
$ wrk -t20 -c20 -d2s http://127.0.0.1:8080

Running 2s test @ http://127.0.0.1:8080
  20 threads and 20 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   159.77us   61.65us   2.51ms   96.65%
    Req/Sec     6.24k   402.70     6.81k    75.24%
  260801 requests in 2.10s, 28.60MB read
  
Requests/sec: 124217.27
Transfer/sec:     13.62MB
```

Async responses, using [promesa](http://funcool.github.io/promesa/latest/):

```clj
(require '[promesa.core :as p])

(defn handler [_]
  (-> (p/promise "Hello, World, Async!")
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

Hello, Async!
```

Performance is still good:

```bash
$ wrk -t20 -c20 -d2s http://127.0.0.1:8080

Running 2s test @ http://127.0.0.1:8080
  20 threads and 20 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   178.01us   59.69us   2.08ms   97.26%
    Req/Sec     5.60k   301.96     6.34k    73.57%
  233921 requests in 2.10s, 25.65MB read

Requests/sec: 111398.27
Transfer/sec:     12.22MB
```

## Status

WIP. 

## License

Copyright © 2019 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.

# pohjavirta

Fast & Non-blocking Clojure wrapper for [Undertow](http://undertow.io/).

## Latest version

[![Clojars Project](http://clojars.org/metosin/pohjavirta/latest-version.svg)](http://clojars.org/metosin/pohjavirta)

## Usage

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
$ wrk -t2 -c16 -d10s http://127.0.0.1:8080
  Running 10s test @ http://127.0.0.1:8080
    2 threads and 16 connections
    Thread Stats   Avg      Stdev     Max   +/- Stdev
      Latency   104.62us   22.88us   1.65ms   89.68%
      Req/Sec    71.66k     3.28k   75.82k    86.14%
    1439599 requests in 10.10s, 183.97MB read
  Requests/sec: 142537.83
  Transfer/sec:     18.22MB

$ wrk -t2 -c16 -d10s http://127.0.0.1:8080
Running 10s test @ http://127.0.0.1:8080
  2 threads and 16 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   103.11us   24.27us   2.24ms   92.39%
    Req/Sec    72.75k     3.21k   76.72k    79.50%
  1447151 requests in 10.00s, 184.93MB read
Requests/sec: 144712.05
Transfer/sec:     18.49MB
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

WIP. See [issues](https://github.com/metosin/pohjavirta/issues) for Roadmap.

## License

Copyright © 2019 [Metosin Oy](http://www.metosin.fi)

Distributed under the Eclipse Public License, the same as Clojure.

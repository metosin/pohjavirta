(ns pohjavirta.server-test
  (:require [clj-http.client :as http]
            [clojure.test :refer [use-fixtures deftest is]]
            [pohjavirta.server :as server]
            [pohjavirta.async :as a]
            [ring.middleware.params :as params]
            [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja])
  (:import [java.io File FileOutputStream FileInputStream]
           (java.nio.file Files)
           (java.nio ByteBuffer)
           (java.util.concurrent CompletableFuture)))

;;;
;;; Utils
;;;
(defn- string-80k []
  (apply str (map char
                  (take (* 8 1024)
                        (apply concat (repeat (range (int \a) (int \z))))))))

;; [a..z]+
(def const-string
  (let [tmp (string-80k)]
    (apply str (repeat 1024 tmp))))

(defn ^File gen-tempfile
  "generate a tempfile, the file will be deleted before jvm shutdown"
  ([size extension]
   (let [tmp (doto
                 (File/createTempFile "tmp_" extension)
               (.deleteOnExit))]
     (with-open [w (FileOutputStream. tmp)]
       (.write w ^bytes (.getBytes (subs const-string 0 size))))
     tmp)))

(defn to-int [^String int-str] (Integer/valueOf int-str))

;;;
;;; Handlers
;;;

(defn file-handler [{{{:keys [length]} :query} :parameters}]
  {:status 200
   :body (gen-tempfile length ".txt")})

(defn inputstream-handler [{{{:keys [length]} :query} :parameters}]
  (let [file (gen-tempfile length ".txt")]
    {:status 200
     :body (FileInputStream. file)}))

(defn bytearray-handler [{{{:keys [length]} :query} :parameters}]
  (let [file (gen-tempfile length ".txt")]
    {:status 200
     :body (Files/readAllBytes (.toPath file))}))

(defn many-headers-handler [req]
  (let [count (or (-> req :params :count to-int) 20)]
    {:status 200
     :headers (assoc
               (into {} (map (fn [idx]
                               [(str "key-" idx) (str "value-" idx)])
                             (range 0 (inc count))))
               "x-header-1" ["abc" "def"])}))

(defn multipart-handler [req]
  (let [{:keys [title file]} (:params req)]
    {:status 200
     :body (str title ": " (:size file))}))

(defn promise-handler [_]
  (-> (a/promise "Hello, Async!")
      (a/then (fn [response]
                {:status 200
                 :headers {"Content-Type" "text/plain"}
                 :body response}))))

(defn many-headers-handler [{{{:keys [count]} :query} :parameters}]
  {:status 200
   :headers (assoc
             (into {} (map (fn [idx]
                             [(str "key-" idx) (str "value-" idx)])
                           (range 0 (inc count))))
             "x-header-1" ["abc" "def"])
   :body nil})

(def app
  (ring/ring-handler
   (ring/router
    [["/file" {:get {:parameters {:query {:length int?}}
                     :handler    file-handler}}]
     ["/inputstream" {:get {:parameters {:query {:length int?}}
                            :handler    inputstream-handler}}]
     ["/bytearray" {:get {:parameters {:query {:length int?}}
                          :handler    bytearray-handler}}]
     ["/iseq" (fn [_req] {:status 200
                         :headers {"Content-Type" "text/plain"}
                         :body (map str (range 1 10))})]
     ["/string" (fn [_req] {:status  200
                           :headers {"Content-Type" "text/plain"}
                           :body    "Hello World"})]
     ["/promise" promise-handler]
     ["/headers" {:get {:parameters {:query {:count int?}}
                        :handler    many-headers-handler}}]
     ["/nil-body" (fn [_] {:status 200})]]
    {:data {:coercion   reitit.coercion.spec/coercion
            :middleware [params/wrap-params
                         muuntaja/format-request-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})))

;;;
;;; Tests
;;;

(use-fixtures :once (fn [f]
                      (let [server (server/create #'app {:port 2040})]
                        (server/start server)
                        (try (f) (finally (server/stop server))))))


(deftest test-body-file
  (doseq [length (range 1 (* 1024 1024 8) 1439987)]
    (let [resp (http/get (str "http://localhost:2040/file?length=" length))]
      (is (= (:status resp) 200))
      (is (= length (count (:body resp)))))))

(deftest test-body-string
  (let [resp (http/get "http://localhost:2040/string")]
    (is (= (:status resp) 200))
    (is (= (:body resp) "Hello World"))))

(deftest test-body-inputstream
  (doseq [length (range 1 (* 1024 1024 5) 1439987)] ; max 5m, many files
    (let [uri (str "http://localhost:2040/inputstream?length=" length)
          resp (http/get uri)]
      (is (= (:status resp) 200))
      (is (= length (count (:body resp)))))))

(deftest test-body-bytearray
  (doseq [length (range 1 (* 1024 1024 5) 1439987)] ; max 5m, many files
    (let [uri (str "http://localhost:2040/bytearray?length=" length)
          resp (http/get uri)]
      (is (= (:status resp) 200))
      (is (= length (count (:body resp)))))))

(deftest test-promise-response
  (let [resp (http/get "http://localhost:2040/promise")]
    (is (= (:status resp) 200))
    (is (= (:body resp) "Hello, Async!"))))

#_(deftest test-body-iseq
  (let [resp (http/get "http://localhost:2040/iseq")]
    (is (= (:status resp) 200))
    (is (= (get-in resp [:headers "content-type"]) "text/plain"))
    (is (= (:body resp) (apply str (range 1 10))))))

(deftest test-many-headers
  (doseq [c (range 5 40)]
    (let [resp (http/get (str "http://localhost:2040/headers?count=" c))]
      (is (= (:status resp) 200))
      (is (= (get-in resp [:headers (str "key-" c)]) (str "value-" c))))))

(deftest test-nil-body
  (let [resp (http/get "http://localhost:2040/nil-body")]
    (is (= (:status resp) 200))
    (is (= "" (:body resp)))))

(ns ^:no-doc pohjavirta.async
  (:refer-clojure :exclude [promise])
  (:import (java.util.concurrent CompletableFuture Executor)
           (java.util.function Function)))

(defn promise
  ([]
   (CompletableFuture.))
  ([x]
   (let [cf (CompletableFuture.)]
     (.complete cf x)
     cf)))

(defn complete [^CompletableFuture cf x]
  (.complete cf x)
  cf)

(defn then [^CompletableFuture cf f]
  (.thenApply cf (reify Function
                   (apply [_ response]
                     (f response)))))

(defn then-async
  ([^CompletableFuture cf f]
   (.thenApplyAsync cf (reify Function
                         (apply [_ response]
                           (f response)))))
  ([^CompletableFuture cf f ^Executor executor]
   (.thenApplyAsync cf (reify Function
                         (apply [_ response]
                           (f response))) executor)))

(defn catch [^CompletableFuture cf f]
  (.exceptionally cf (reify Function
                       (apply [_ exception]
                         (f exception)))))

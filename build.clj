(ns build
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.tools.build.api :as b]
   [deps-deploy.deps-deploy :as deploy]))

(def lib 'metosin/pohjavirta)
(def version "0.0.1-alpha8")
(def class-dir "target")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn compile [_]
  (b/javac {:src-dirs ["src"]
            :class-dir class-dir
            :basis basis
            :javac-opts ["-source" "8" "-target" "8"]}))

(defn pom [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]}))

(defn jar [_]
  (compile nil)
  (pom nil)
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(def deploy-opts
  {:artifact       jar-file
   :pom-file       (format "%s/META-INF/maven/%s/pom.xml" class-dir lib)
   :sign-releases? false})

(defn install [_]
  (jar nil)
  (deploy/deploy (assoc deploy-opts :installer :local)))

(defn deploy [_]
  (jar nil)
  (try
    (deploy/deploy (assoc deploy-opts :installer :remote))
    (catch Exception e
      (when (re-find #"Unauthorized" (ex-message e))
        (println ">>> Set CLOJARS_USERNAME and CLOJARS_PASSWORD environment variables to deploy <<<<"))
      (throw e))))

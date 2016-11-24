(ns sente-websockets-rabbitmq.config
  (:require [environ.core :refer [env]]))

(def config (try (clojure.edn/read-string (slurp (clojure.java.io/resource "config.edn"))) (catch Throwable e {})))

(defn get-property [property default]
  (or (env property) (property config) default))

(ns sente-websockets-rabbitmq.config
  (:require [environ.core :refer [env]]))

(def defaults
  {:redis-url "redis://user:pass@localhost:6379"
   :oauth-callback "http://localhost:10555/login"
   :oauth-api-key ""
   :oauth-api-secret ""
   :db-host ""
   :db-user ""
   :db-pass ""
   :amqp-host "localhost"
   :amqp-port 5672
   :amqp-user "guest"
   :amqp-pass "guest"
   :rabbitmq-bigwig-rx-url nil
   :url "/"
   :port 10555})

(def config
  (merge defaults
         (try (clojure.edn/read-string (slurp (clojure.java.io/resource "config.edn"))) (catch Throwable e {}))))

(defn get-property [property]
  (or (env property) (property config)))

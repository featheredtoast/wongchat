(ns sente-websockets-rabbitmq.application
  (:require
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.gzip :refer [wrap-gzip]]
   [ring.middleware.logger :refer [wrap-with-logger]]
   [com.stuartsierra.component :as component]
   [system.components.http-kit :refer [new-web-server]]
   [system.components.sente :refer [new-channel-sockets sente-routes]]
   [system.components.endpoint :refer [new-endpoint]]
   [system.components.handler :refer [new-handler]]
   [system.components.middleware :refer [new-middleware]]
   [system.components.rabbitmq :refer [new-rabbit-mq]]
   [taoensso.sente.server-adapters.http-kit :refer [sente-web-server-adapter]]
   [clj-redis-session.core :as redis-session]
   [sente-websockets-rabbitmq.config :refer [config]]
   [sente-websockets-rabbitmq.db :as db]
   [sente-websockets-rabbitmq.routes :refer [routes]]
   [sente-websockets-rabbitmq.events :as events]
   [sente-websockets-rabbitmq.auth :as auth])
  (:gen-class))

(defn app-system [{:keys [rabbitmq-bigwig-rx-url
                          amqp-user amqp-pass amqp-host amqp-port
                          url]
                   :as config}]
  (let [redis-conn {:spec {:uri (:redis-url config)}}
        port (Integer. (:port config))
        rabbitmq-uri (or rabbitmq-bigwig-rx-url
                         (str "amqp://" amqp-user ":"
                              amqp-pass "@" amqp-host ":" amqp-port))]
    (println "amqp uri " rabbitmq-uri)
    (component/system-map
     :db-migrate (db/new-migrate)
     :rabbit-mq (new-rabbit-mq rabbitmq-uri)
     :sente (component/using
             (new-channel-sockets
              events/sente-handler
              sente-web-server-adapter
              {:wrap-component? true
               :user-id-fn auth/get-user-id})
             [:rabbit-mq])
     :post-handler (component/using
                    (events/new-messager)
                    [:sente :rabbit-mq])
     :sente-endpoint (component/using
                      (new-endpoint sente-routes)
                      [:sente])
     :routes (new-endpoint (partial routes url))
     :middleware (new-middleware
                  {:middleware [[wrap-defaults (assoc site-defaults :session {:store (redis-session/redis-store redis-conn)})]
                                wrap-with-logger
                                wrap-gzip]})
     :handler (component/using
               (new-handler)
               [:sente-endpoint :routes :middleware])
     :http (component/using
            (new-web-server port)
            [:handler]))))

(defn -main [& [port]]
  (component/start (app-system config)))

(ns sente-websockets-rabbitmq.application
  (:require [clojure.java.io :as io]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            [system.components.http-kit :refer [new-web-server]]
            [system.components.sente :refer [new-channel-sockets sente-routes]]
            [system.components.endpoint :refer [new-endpoint]]
            [system.components.handler :refer [new-handler]]
            [system.components.middleware :refer [new-middleware]]
            [system.components.rabbitmq :refer [new-rabbit-mq]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! take! chan go go-loop)]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit      :refer (sente-web-server-adapter)]
            [clj-redis-session.core :as redis-session]
            [sente-websockets-rabbitmq.config :refer [get-property]]
            [sente-websockets-rabbitmq.db :as db]
            [sente-websockets-rabbitmq.routes :refer [routes]]
            [sente-websockets-rabbitmq.events :as events]
            [sente-websockets-rabbitmq.auth :as auth])
  (:gen-class))

(def redis-conn {:spec {:uri (get-property :redis-url)}})


(defn app-system []
  (component/system-map
   :db-migrate (db/new-migrate)
   :rabbit-mq (new-rabbit-mq (events/rabbitmq-config))
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
   :routes (new-endpoint routes)
   :middleware (new-middleware  {:middleware [[wrap-defaults :defaults]
                                              wrap-with-logger
                                              wrap-gzip]
                                 :defaults (assoc site-defaults :session {:store (redis-session/redis-store redis-conn)})})
   :handler (component/using
             (new-handler)
             [:sente-endpoint :routes :middleware])
   :http (component/using
          (new-web-server (Integer. (get-property :port)))
          [:handler])))

(defn -main [& [port]]
  (component/start (app-system)))

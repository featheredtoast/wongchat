(ns sente-websockets-rabbitmq.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.reload :refer [wrap-reload]]
            [environ.core :refer [env]]
            [com.stuartsierra.component :as component]
            (system.components
             [http-kit :refer [new-web-server]]
             [sente :refer [new-channel-sockets sente-routes]]
             [endpoint :refer [new-endpoint]]
             [handler :refer [new-handler]]
             [middleware :refer [new-middleware]]
             [rabbitmq :refer [new-rabbit-mq]])
            [reloaded.repl :refer [system init start stop reset reset-all]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! take! chan go go-loop)]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit      :refer (sente-web-server-adapter)]
            [cemerick.friend :as friend]
            [clj-redis-session.core :as redis-session]
            [sente-websockets-rabbitmq.config :refer [get-property]]
            [sente-websockets-rabbitmq.db :as db]
            [sente-websockets-rabbitmq.events :as events]
            [sente-websockets-rabbitmq.auth :as auth])
  (:gen-class))

(def redis-conn {:spec {:uri (get-property :redis-url "redis://user:pass@localhost:6379")}})

(defroutes routes
  (GET "/" _
       {:status 200
        :headers {"Content-Type" "text/html; charset=utf-8"}
        :body (io/input-stream (io/resource "public/index.html"))})
  (GET "/chat"
       req
       (friend/authorize
        #{:user}
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (io/input-stream (io/resource "public/chat.html"))
         :cookies {"user" {:value (auth/get-user-id req)}}}))
  (resources "/")
  (friend/logout (ANY "/logout" request (ring.util.response/redirect (get-property :url "/")))))

(defn get-http-handler [config]
  (-> routes
      (friend/authenticate
       {:workflows [auth/workflow] :auth-url "/login"
        :credential-fn auth/credential-fn})))

(defn prod-system []
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
   :routes (new-endpoint get-http-handler)
   :middleware (new-middleware  {:middleware [[wrap-defaults :defaults]
                                              wrap-with-logger
                                              wrap-gzip]
                                 :defaults (assoc site-defaults :session {:store (redis-session/redis-store redis-conn)})})
   :handler (component/using
             (new-handler)
             [:sente-endpoint :routes :middleware])
   :http (component/using
          (new-web-server (Integer. (or (env :port) 10555)))
          [:handler])))

(defn run-prod []
  (reloaded.repl/set-init! prod-system)
  (reloaded.repl/go))

(defn -main [& [port]]
  (run-prod))

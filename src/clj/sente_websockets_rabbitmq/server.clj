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
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb]
            [cemerick.friend :as friend]
            [clojure.data.json :as json]
            [clj-redis-session.core :as redis-session]
            [sente-websockets-rabbitmq.config :refer [get-property]]
            [sente-websockets-rabbitmq.db :as db]
            [sente-websockets-rabbitmq.auth :as auth])
  (:gen-class))

(def redis-conn {:spec {:uri (get-property :redis-url "redis://user:pass@localhost:6379")}})

(defn rabbitmq-config []
  (let [host (get-property :amqp-host "localhost")
        port (get-property :amqp-port 5672)
        username (get-property :amqp-user "guest")
        password (get-property :amqp-pass "guest")
        uri (str "amqp://" username ":" password "@" host ":" port)
        final-uri (or (get-property :rabbitmq-bigwig-rx-url nil) uri)]
    (println "amqp uri " final-uri)
    final-uri))

(defmulti event-msg-handler (fn [_ msg] (:id msg))) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [rabbit-data {:as ev-msg :keys [id ?data event]}]
  (event-msg-handler rabbit-data ev-msg))
(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [_ {:as ev-msg :keys [event]}]
    (println "Unhandled event: %s" event))
  
  (defmethod event-msg-handler :chsk/ws-ping
    [_ {:as ev-msg :keys [event]}])

  (defmethod event-msg-handler :chsk/uidport-open
    [_ {:as ev-msg :keys [uid]}]
    (println "new client connection"))

  (defmethod event-msg-handler :chat/init
    [_ {:as ev-msg :keys [?data uid send-fn]}]
    (println "init: " uid)
    (send-fn uid
             [:chat/init
              (reverse (vec (db/get-recent-messages)))]))

  (defmethod event-msg-handler :chat/submit
    [rabbit-data {:as ev-msg :keys [?data uid]}]
    (let [msg (:msg ?data)]
      (println "Event from " uid ": " msg)
      (db/insert-message uid msg)
      (lb/publish (:ch rabbit-data) "" (:qname rabbit-data) (json/write-str {:msg msg :uid uid}) {:content-type "text/json" :type "greetings.hi"}))))

(defn sente-handler [{:keys [rabbit-mq]}]
  (let [{:keys [ch]} rabbit-mq]
    (partial event-msg-handler* {:ch ch :qname "hello"})))

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

(defn message-handler
  [chsk-send! connected-uids
   ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [payload (json/read-str (String. payload "UTF-8")
                               :key-fn keyword)
        msg (:msg payload)
        sender-uid (:uid payload)]
    (println "Broadcasting server>user: %s" @connected-uids)
    (println "sending: " payload)
    (doseq [uid (:any @connected-uids)]
      (chsk-send! uid
                  [:chat/message
                   {:msg msg
                    :uid sender-uid}]))))

(defrecord Messager [sente rabbit-mq]
  component/Lifecycle
  (start [component]
    (let [qname "hello"
          {:keys [chsk-send! connected-uids]} sente
          {:keys [ch]} rabbit-mq]
      (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
      (lq/declare ch qname {:exclusive false :auto-delete false})
      (lc/subscribe ch qname (partial message-handler chsk-send! connected-uids) {:auto-ack true})
      component))
  (stop [component]
    component))
(defn new-messager []
  (map->Messager {}))

(defn prod-system []
  (component/system-map
   :db-migrate (db/new-migrate)
   :rabbit-mq (new-rabbit-mq (rabbitmq-config))
   :sente (component/using
           (new-channel-sockets sente-handler sente-web-server-adapter {:wrap-component? true
                                                                        :user-id-fn auth/get-user-id})
           [:rabbit-mq])
   :post-handler (component/using
                  (new-messager)
                  [:sente :rabbit-mq])
   :sente-endpoint (component/using
                    (new-endpoint sente-routes)
                    [:sente])
   :routes (new-endpoint get-http-handler)
   :middleware (new-middleware  {:middleware [[wrap-defaults :defaults]
                                              wrap-with-logger
                                              wrap-gzip
                                              wrap-reload]
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

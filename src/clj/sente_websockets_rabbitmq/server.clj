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
            [qarth.friend]
            [qarth.oauth :as oauth]
            [qarth.impl.google]
            [clojure.data.json :as json]
            [ragtime.jdbc]
            [ragtime.repl]
            [clojure.java.jdbc :as jdbc]
            [clj-redis-session.core :as redis-session])
  (:gen-class))

(def config (try (clojure.edn/read-string (slurp (clojure.java.io/resource "config.edn"))) (catch Throwable e {})))

(defn get-property [property default]
  (or (env property) (property config) default))

(defn credential-fn [id]
  (let [email (get-in id [:qarth.oauth/record :email])]
    (assoc id :roles [::user])))

(def db-config
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (get-property :db-host "")
   :user (get-property :db-user "")
   :password (get-property :db-pass "")})

(def redis-conn {:spec {:uri (get-property :redis-url "redis://user:pass@localhost:6379")}})

(def conf {:type :google
           :callback (get-property :oauth-callback "http://localhost:3449/login")
           :api-key (get-property :oauth-api-key "")
           :api-secret (get-property :oauth-api-secret "")})

(def service (oauth/build conf))

(def workflow
  (qarth.friend/oauth-workflow
   {:service service
    :login-failure-handler
    (fn [_] (ring.util.response/redirect
             "/login?exception=true"))}))

(defn get-user-id [req]
  (let [id (-> req (qarth.friend/requestor service) oauth/id)
            email (get-in req [:session :cemerick.friend/identity :authentications :qarth.oauth/anonymous :qarth.oauth/record :email])
            friend-attributes (-> req (qarth.friend/auth-record))]
       (println (format "user id %s" email))
       email))

#_(defn start-sente! []
  (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
                connected-uids]}
        (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn get-user-id})]
    (def ring-ajax-post                ajax-post-fn)
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
    (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
    (def connected-uids                connected-uids) ; Watchable, read-only atom
    ))

#_(defn connect-amqp! []
  (let [host (get-property :amqp-host "localhost")
        port (get-property :amqp-port 5672)
        username (get-property :amqp-user "guest")
        password (get-property :amqp-pass "guest")
        uri (str "amqp://" username ":" password "@" host ":" port)]
    (println "amqp uri " (or (get-property :rabbitmq-bigwig-rx-url nil) uri))
    (def conn  (rmq/connect {:uri (or (get-property :rabbitmq-bigwig-rx-url nil) uri)})))
  (def ch    (lch/open conn))
    (def qname "hello"))

(defn rabbitmq-config []
  (let [host (get-property :amqp-host "localhost")
        port (get-property :amqp-port 5672)
        username (get-property :amqp-user "guest")
        password (get-property :amqp-pass "guest")
        uri (str "amqp://" username ":" password "@" host ":" port)]
    (println "amqp uri " (or (get-property :rabbitmq-bigwig-rx-url nil) uri))
    uri))

#_(defn disconnect-amqp! []
  (rmq/close ch)
  (rmq/close conn))

(defn get-recent-messages []
  (jdbc/query db-config
              ["select uid, msg from messages order by id DESC LIMIT 10;"]))

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))
(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event]}]
    (println "Unhandled event: %s" event))
  
  (defmethod event-msg-handler :chsk/ws-ping
    [{:as ev-msg :keys [event]}])

  (defmethod event-msg-handler :chsk/uidport-open
    [{:as ev-msg :keys [uid]}]
    (println "new client connection"))

  (defmethod event-msg-handler :chat/init
    [{:as ev-msg :keys [?data uid send-fn]}]
    (println "init: " uid)
    (send-fn uid
             [:chat/init
              (reverse (vec (get-recent-messages)))]))

  (defmethod event-msg-handler :chat/submit
    [{:as ev-msg :keys [?data uid]}]
    (let [msg (:msg ?data)]
      (println "Event from " uid ": " msg)
      #_(jdbc/insert! db-config :messages
                    {:uid uid :msg msg})
      #_(lb/publish ch "" qname (json/write-str {:msg msg :uid uid}) {:content-type "text/json" :type "greetings.hi"}))))

(defn sente-handler [config]
  event-msg-handler*)

(defroutes routes
  (GET "/" _
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (GET "/chat"
       req
       (friend/authorize
        #{::user}
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (io/input-stream (io/resource "public/chat.html"))
         :cookies {"user" {:value (get-user-id req)}}}))
  (resources "/")
  (friend/logout (ANY "/logout" request (ring.util.response/redirect (get-property :url "/")))))

#_(defn message-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
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

#_(defn start-broadcaster! []
  (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
  (lq/declare ch qname {:exclusive false :auto-delete false})
  (lc/subscribe ch qname message-handler {:auto-ack true}))

(def http-handler
  (-> routes
      (friend/authenticate
       {:workflows [workflow] :auth-url "/login"
        :credential-fn credential-fn})
      (wrap-defaults (assoc site-defaults :session {:store (redis-session/redis-store redis-conn)}))
      wrap-with-logger
      wrap-gzip))

(defn get-http-handler [config]
  (-> routes
      (friend/authenticate
       {:workflows [workflow] :auth-url "/login"
        :credential-fn credential-fn})))

(defn db-migrate! []
  (ragtime.repl/migrate {:datastore
   (ragtime.jdbc/sql-database db-config)
                         :migrations (ragtime.jdbc/load-resources "migrations")}))

(defn start-workers! []
  (db-migrate!)
  #_(connect-amqp!)
  #_(start-broadcaster!))

(defn stop-workers! []
  #_(disconnect-amqp!))

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

(defrecord Messager [ch chsk-send! connected-uids]
  component/Lifecycle
  (start [component]
    (let [qname "hello"]
      (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
      (lq/declare ch qname {:exclusive false :auto-delete false})
      (lc/subscribe ch qname (partial message-handler chsk-send! connected-uids) {:auto-ack true})))
  (stop [component]
    component))
(defn new-messager []
  (map->Messager {}))

(defn prod-system []
  (component/system-map
   :sente (new-channel-sockets sente-handler sente-web-server-adapter {:wrap-component? true
                                                                       :user-id-fn get-user-id})
   :rabbit-mq (new-rabbit-mq (rabbitmq-config))
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
  (db-migrate!)
  (run-prod))

#_(defn -main [& [port]]
  (start-workers!)
  (let [port (Integer. (or port (env :port) 10555))]
    (run-server http-handler {:port port :join? false})))

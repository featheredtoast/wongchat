(ns sente-websockets-rabbitmq.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
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
            [clojure.data.json :as json])
  (:gen-class))

(defn credential-fn [id]
  (let [email (get-in id [:qarth.oauth/record :email])]
    (assoc id :roles [::user])))

(def conf {:type :google
           :callback (or (env :oauth-callback) "http://localhost:3449/login")
           :api-key (or (env :oauth-api-key) "")
           :api-secret (or (env :oauth-api-secret) "")})

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

(defn start-sente! []
  (let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
                connected-uids]}
        (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn get-user-id})]
    (def ring-ajax-post                ajax-post-fn)
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
    (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
    (def connected-uids                connected-uids) ; Watchable, read-only atom
    ))

(defn connect-amqp! []
  (let [host (or (env :amqp-host) "localhost")
        port (or (env :amqp-port) 5672)
        username (or (env :amqp-user) "guest")
        password (or (env :amqp-pass) "guest")
        uri (str "amqp://" username ":" password "@" host ":" port)]
    (println "amqp uri " (or (env :rabbitmq-bigwig-rx-url) uri))
    (defonce conn  (rmq/connect {:uri (or (env :rabbitmq-bigwig-rx-url) uri)})))
  (defonce ch    (lch/open conn))
  (defonce qname "hello"))


(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))
(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event]}]
    (println "Unhandled event: %s" event))
  
  (defmethod event-msg-handler :chsk/ws-ping
    [{:as ev-msg :keys [event]}]
    #_(println "ping"))

  (defmethod event-msg-handler :some/request-id
    [{:as ev-msg :keys [?data uid]}]
    (let [msg (:msg ?data)]
      (println "Event from " uid ": " msg)
      (lb/publish ch "" qname (json/write-str {:msg msg :uid uid}) {:content-type "text/json" :type "greetings.hi"}))))

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
  (GET  "/chsk" req
        (friend/authorize
         #{::user}
         (ring-ajax-get-or-ws-handshake req)))
  (POST "/chsk" req
        (friend/authorize
         #{::user}
         (ring-ajax-post                req)))
  (friend/logout (ANY "/logout" request (ring.util.response/redirect "/"))))

(defn message-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [payload (json/read-str (String. payload "UTF-8")
                               :key-fn keyword)
        msg (:msg payload)
        sender (:uid payload)]
    (println "Broadcasting server>user: %s" @connected-uids)
    (println "sending: " payload)
    (doseq [uid (:any @connected-uids)]
      (chsk-send! uid
                  [:some/broadcast
                   {:what-is-this "Broadcast from amqp"
                    :to-whom uid
                    :msg msg
                    :sender sender}]))))

(defn start-broadcaster! []
  (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
  (lq/declare ch qname {:exclusive false :auto-delete false})
  (lc/subscribe ch qname message-handler {:auto-ack true}))

(def http-handler
  (-> routes
      (friend/authenticate
       {:workflows [workflow] :auth-url "/login"
        :credential-fn credential-fn})
      (wrap-defaults site-defaults)
      wrap-with-logger
      wrap-gzip))

(defn start-workers! []
  (start-sente!)
  (connect-amqp!)
  (sente/start-chsk-router! ch-chsk event-msg-handler*)
  (start-broadcaster!))

(defn -main [& [port]]
  (start-workers!)
  (let [port (Integer. (or port (env :port) 10555))]
    (run-server http-handler {:port port :join? false})))

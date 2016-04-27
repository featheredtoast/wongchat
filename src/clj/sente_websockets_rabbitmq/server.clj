(ns sente-websockets-rabbitmq.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit      :refer (sente-web-server-adapter)]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb])
  (:gen-class))

(defn get-user-id [req]
  (println (format "user id %s" (get-in req [:session :base-user-id])))
  "user")

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn
              connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:user-id-fn get-user-id})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

(let [host (or (env :amqp-host) "172.17.0.2")
      port (or (env :amqp-port) 5672)
      username (or (env :amqp-user) "guest")
      password (or (env :amqp-pass) "guest")]
  (defonce conn  (rmq/connect {:host host
                               :port port
                               :username username
                               :password password})))
(defonce ch    (lch/open conn))
(defonce qname "hello")


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
    (println "ping"))

  (defmethod event-msg-handler :some/request-id
    [{:as ev-msg :keys [?data]}]
    (let [msg (:msg ?data)]
      (println "Event from client: " msg)
      (lb/publish ch "" qname msg {:content-type "text/plain" :type "greetings.hi"}))))
(sente/start-chsk-router! ch-chsk event-msg-handler*)

(defroutes routes
  (GET "/" _
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (resources "/")
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post                req)))

(defn message-handler
  [ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [payload (String. payload "UTF-8")]
    (println "Broadcasting server>user: %s" @connected-uids)
    (doseq [uid (:any @connected-uids)]
      (chsk-send! uid
                  [:some/broadcast
                   {:what-is-this "Broadcast from amqp"
                    :to-whom uid
                    :i payload}]))))

(defn start-broadcaster! []
  (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
  (lq/declare ch qname {:exclusive false :auto-delete false})
  (lc/subscribe ch qname message-handler {:auto-ack true}))

(start-broadcaster!)

(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-jetty http-handler {:port port :join? false})))

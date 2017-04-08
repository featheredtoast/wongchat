(ns sente-websockets-rabbitmq.routes
  (:require
   [bidi.bidi :as bidi]
   [bidi.ring]
   [cemerick.friend :as friend]
   [sente-websockets-rabbitmq.html.index :as html]
   [sente-websockets-rabbitmq.auth :as auth]
   [sente-websockets-rabbitmq.db :as db]))

(defn get-initial-state [uid]
  (let [channel "#general"]
    {:messages (db/get-recent-messages)
     :typing #{}
     :channel-data {channel
                    {:messages (db/get-recent-messages channel)
                     :typing #{}}}
     :active-channel channel
     :user-typing false
     :input ""
     :initializing true
     :latest-input ""
     :connected false
     :user uid
     :message-history (db/get-user-messages uid)
     :message-history-position 0
     :menu {:px-open 0
            :max-px-width 201}
     :network-up? true
     :subscribed? false
     :push-key (db/get-public-server-credentials)}))

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (html/login)})

(defn chat [req]
  (println "yay chat...")
  (friend/authorize
   #{:user}
   (let [uid (auth/get-user-id req)
         initial-state (get-initial-state uid)]
     {:status 200
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body (html/chat initial-state)})))

(defn subscribe [req]
  (friend/authorize
   #{:user}
   (let [uid (auth/get-user-id req)
         subscription (get-in req [:params :subscription])]
     (db/save-subscription uid subscription)
     {:status 200
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body "ok"})))

(defn unsubscribe [req]
  (friend/authorize
   #{:user}
   (let [uid (auth/get-user-id req)
         subscription (get-in req [:params :subscription])]
     (db/delete-subscription-by-subscription subscription)
     {:status 200
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body "ok"})))

(defn logout [req]
  (friend/logout* (ring.util.response/redirect "/")))

(def bidi-routes ["/"
                  [["" :index]
                   ["chat" :chat]
                   ["subscribe" :subscribe]
                   ["unsubscribe" :unsubscribe]
                   ["logout" :logout]
                   ["" (bidi.ring/->ResourcesMaybe {:prefix "public/"})]]])

(def bidi-route-handlers
  {:index index
   :chat chat
   :subscribe subscribe
   :unsubscribe unsubscribe
   :logout logout})

(defn routes [url _]
  (let [basic-routes
        (bidi.ring/make-handler bidi-routes
                                bidi-route-handlers)]
    (-> basic-routes
        (friend/authenticate
         {:workflows [auth/workflow] :auth-url "/login"
          :credential-fn auth/credential-fn}))))

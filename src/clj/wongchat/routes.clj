(ns wongchat.routes
  (:require
   [bidi.bidi :as bidi]
   [bidi.ring]
   [cemerick.friend :as friend]
   [wongchat.html.index :as html]
   [wongchat.auth :as auth]
   [wongchat.db :as db]
   [wongchat.router :as router]
   [wongchat.config :refer [config]]))

(defn get-initial-state [uid channel]
  {:messages (db/get-recent-messages channel)
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
   :push-key (db/get-public-server-credentials)
   :base-url (:base-url (config))})

(defn index [req]
  (println "hihihihihi")
  {:status 200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body (html/login)})

(defn chat [{:keys [route-params] :as req}]
  (let [channel (if (:channel route-params)
                  (str "#" (:channel route-params))
                  "#general")]
    (friend/authorize
     #{:user}
     (let [uid (auth/get-user-id req)
           initial-state (get-initial-state uid channel)]
       {:status 200
        :headers {"Content-Type" "text/html; charset=utf-8"}
        :body (html/chat initial-state)}))))

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
  (friend/logout* (ring.util.response/redirect (str (:base-url (config)) "/"))))

(def route-handlers
  {:index index
   :chat chat
   :chat-index chat
   :subscribe subscribe
   :unsubscribe unsubscribe
   :logout logout})

(defn routes [url]
  (let [basic-routes
        (bidi.ring/make-handler router/routes
                                route-handlers)]
    (-> basic-routes
        (friend/authenticate
         {:workflows [auth/workflow] :auth-url "/login"
          :credential-fn auth/credential-fn}))))

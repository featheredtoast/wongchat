(ns sente-websockets-rabbitmq.routes
  (:require
   [compojure.core :as compojure :refer [ANY GET PUT POST DELETE]]
   [compojure.route :refer [resources]]
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

(defn routes [url _]
  (let [basic-routes
        (compojure/routes
         (GET "/" _
              {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"}
               :body (html/login)})
         (GET "/chat"
              req
              (friend/authorize
               #{:user}
               (let [uid (auth/get-user-id req)
                     initial-state (get-initial-state uid)]
                 {:status 200
                  :headers {"Content-Type" "text/html; charset=utf-8"}
                  :body (html/chat initial-state)})))
         (POST "/subscribe"
              req
              (friend/authorize
               #{:user}
               (let [uid (auth/get-user-id req)
                     subscription (get-in req [:params :subscription])]
                 (db/save-subscription uid subscription)
                 {:status 200
                  :headers {"Content-Type" "text/html; charset=utf-8"}
                  :body "ok"})))
         (POST "/unsubscribe"
              req
              (friend/authorize
               #{:user}
               (let [uid (auth/get-user-id req)
                     subscription (get-in req [:params :subscription])]
                 (db/delete-subscription-by-subscription subscription)
                 {:status 200
                  :headers {"Content-Type" "text/html; charset=utf-8"}
                  :body "ok"})))
         (resources "/")
         (friend/logout (ANY "/logout" request (ring.util.response/redirect url))))]
    (-> basic-routes
        (friend/authenticate
         {:workflows [auth/workflow] :auth-url "/login"
          :credential-fn auth/credential-fn}))))

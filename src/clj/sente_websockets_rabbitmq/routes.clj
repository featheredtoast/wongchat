(ns sente-websockets-rabbitmq.routes
  (:require
   [compojure.core :as compojure :refer [ANY GET PUT POST DELETE]]
   [compojure.route :refer [resources]]
   [cemerick.friend :as friend]
   [sente-websockets-rabbitmq.html.index :as html]
   [sente-websockets-rabbitmq.auth :as auth]
   [sente-websockets-rabbitmq.db :as db]
   [cognitect.transit :as transit]))

(defn get-initial-state [uid]
  {:messages (db/get-recent-messages)
   :typing #{}
   :user-typing false
   :input ""
   :latest-input ""
   :user uid
   :message-history (db/get-user-messages uid)
   :message-history-position 0})

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
                     initial-state (get-initial-state uid)
                     out (java.io.ByteArrayOutputStream. 4096)
                     writer (transit/writer out :json)
                     message (do (transit/write writer initial-state)
                                 (println (.toString out))
                                 (.toString out))]
                 {:status 200
                  :headers {"Content-Type" "text/html; charset=utf-8"}
                  :body (html/chat initial-state)
                  :cookies {"user" {:value uid}
                            "app-state" {:value
                                         (. java.net.URLEncoder
                                            encode
                                            message
                                            "UTF-8")}}})))
         (resources "/")
         (friend/logout (ANY "/logout" request (ring.util.response/redirect url))))]
    (-> basic-routes
        (friend/authenticate
         {:workflows [auth/workflow] :auth-url "/login"
          :credential-fn auth/credential-fn}))))

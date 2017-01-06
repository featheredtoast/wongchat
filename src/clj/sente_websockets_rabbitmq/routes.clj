(ns sente-websockets-rabbitmq.routes
  (:require
   [compojure.core :as compojure :refer [ANY GET PUT POST DELETE]]
   [compojure.route :refer [resources]]
   [cemerick.friend :as friend]
   [sente-websockets-rabbitmq.html.index :as html]
   [sente-websockets-rabbitmq.auth :as auth]
   [sente-websockets-rabbitmq.db :as db]
   [clojure.data.json :as json]))

(defn get-initial-state [uid]
  {:messages (db/get-recent-messages)
   :typing #{}
   :user-typing false
   :input ""
   :user uid})

(defn routes [url _]
  (let [basic-routes
        (compojure/routes
         (GET "/" _
              {:status 200
               :headers {"Content-Type" "text/html; charset=utf-8"}
               :body (html/login)})
         (GET "/chat"
              req
              (let [uid (auth/get-user-id req)
                    initial-state (get-initial-state uid)]
                (friend/authorize
                 #{:user}
                 {:status 200
                  :headers {"Content-Type" "text/html; charset=utf-8"}
                  :body (html/chat initial-state)
                  :cookies {"user" {:value uid}
                            "app-state" {:value
                                         (. java.net.URLEncoder
                                            encode
                                            (json/write-str
                                             initial-state)
                                            "UTF-8")}}})))
         (resources "/")
         (friend/logout (ANY "/logout" request (ring.util.response/redirect url))))]
    (-> basic-routes
        (friend/authenticate
         {:workflows [auth/workflow] :auth-url "/login"
          :credential-fn auth/credential-fn}))))

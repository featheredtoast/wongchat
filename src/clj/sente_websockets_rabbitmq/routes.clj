(ns sente-websockets-rabbitmq.routes
  (:require
   [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
   [compojure.route :refer [resources]]
   [cemerick.friend :as friend]
   [sente-websockets-rabbitmq.html.index :as html]
   [sente-websockets-rabbitmq.auth :as auth]
   [sente-websockets-rabbitmq.config :refer [get-property]]))

(defroutes basic-routes
  (GET "/" _
       {:status 200
        :headers {"Content-Type" "text/html; charset=utf-8"}
        :body (html/login)})
  (GET "/chat"
       req
       (friend/authorize
        #{:user}
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (html/chat)
         :cookies {"user" {:value (auth/get-user-id req)}}}))
  (resources "/")
  (friend/logout (ANY "/logout" request (ring.util.response/redirect (get-property :url)))))

(defn routes [config]
  (-> basic-routes
      (friend/authenticate
       {:workflows [auth/workflow] :auth-url "/login"
        :credential-fn auth/credential-fn})))

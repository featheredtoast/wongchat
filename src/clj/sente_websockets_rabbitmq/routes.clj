(ns sente-websockets-rabbitmq.routes
  (:require
   [compojure.core :as compojure :refer [ANY GET PUT POST DELETE]]
   [compojure.route :refer [resources]]
   [cemerick.friend :as friend]
   [sente-websockets-rabbitmq.html.index :as html]
   [sente-websockets-rabbitmq.auth :as auth]))

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
               {:status 200
                :headers {"Content-Type" "text/html; charset=utf-8"}
                :body (html/chat)
                :cookies {"user" {:value (auth/get-user-id req)}}}))
         (resources "/")
         (friend/logout (ANY "/logout" request (ring.util.response/redirect url))))]
    (-> basic-routes
        (friend/authenticate
         {:workflows [auth/workflow] :auth-url "/login"
          :credential-fn auth/credential-fn}))))

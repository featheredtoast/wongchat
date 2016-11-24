(ns sente-websockets-rabbitmq.auth
  (:require [cemerick.friend :as friend]
            [qarth.friend]
            [qarth.oauth :as oauth]
            [qarth.impl.google]
            [sente-websockets-rabbitmq.config :refer [get-property]]))

(defn credential-fn [id]
  (let [email (get-in id [:qarth.oauth/record :email])]
    (assoc id :roles [:user])))

(def conf {:type :google
           :callback (get-property :oauth-callback "http://localhost:10555/login")
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

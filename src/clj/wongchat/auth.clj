(ns wongchat.auth
  (:require [cemerick.friend :as friend]
            [qarth.friend]
            [qarth.oauth :as oauth]
            [qarth.impl.google]
            [wongchat.config :refer [config]]))

(defn credential-fn [id]
  (let [email (get-in id [:qarth.oauth/record :email])]
    (assoc id :roles [:user])))

(def conf {:type :google
           :callback (:oauth-callback config)
           :api-key (:oauth-api-key config)
           :api-secret (:oauth-api-secret config)})

(def service (oauth/build conf))

(def workflow
  (qarth.friend/oauth-workflow
   {:service service
    :login-failure-handler
    (fn [_] (ring.util.response/redirect
             (str (:base-url config) "/login?exception=true")))}))

(defn get-user-id [req]
  (let [id (-> req (qarth.friend/requestor service) oauth/id)
            email (get-in req [:session :cemerick.friend/identity :authentications :qarth.oauth/anonymous :qarth.oauth/record :email])
            friend-attributes (-> req (qarth.friend/auth-record))]
       (println (format "user id %s" email))
       email))

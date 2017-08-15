(ns wongchat.html.index
  (:require [hiccup.core :refer [html]]
            [wongchat.views :as app]
            [clojure.zip :as zip]
            [wongchat.data :refer [serialize]]))

(def bootstrap-headers
  [[:link {:rel "stylesheet"
           :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css"
           :integrity "sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7"
           :crossorigin "anonymous"}]
   [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"}]
   [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js"
             :integrity "sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS"
             :crossorigin "anonymous"}]])

(def basic-headers
  [[:meta {:charset "UTF-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]])

(def app-css
  [[:link {:href "/css/style.css"
            :rel "stylesheet"
            :type "text/css"}]])

(defn add-headers [html headers]
  (-> (zip/vector-zip html)
      zip/down
      zip/next
      (zip/edit #(apply (partial conj %) headers))
      zip/root))

(defn login []
  (-> [:html
       [:head]
       [:body
        [:div {:class "app container"}
         [:h1 "A wild chat application appears"]
         [:p "Hi there! This is a pretty basic chat thing implemented in clojure/script, websockets, and rabbitmq with authentication over oauth!"]
         [:a {:type "button" :class "btn btn-default" :href "/chat"} "login with google"]]]]
      (add-headers basic-headers)
      (add-headers bootstrap-headers)
      html))

(defn chat [state]
  (-> [:html
       [:head
        [:link {:rel "manifest"
                :href "/manifest.json"}]]
       [:body
        [:div {:id "app"} (app/init-main-app (atom state))]
        [:div {:id "initial-state" :style "display:none;"} (serialize state)]
        [:script "function urlB64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - base64String.length % 4) % 4);
  const base64 = (base64String + padding)
    .replace(/\\-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}"]
        [:script {:src "/js/compiled/wongchat.js"}]
        [:script {:type "text/javascript"} "wongchat.system.go();"]]]
      (add-headers basic-headers)
      (add-headers bootstrap-headers)
      (add-headers app-css)
      html))

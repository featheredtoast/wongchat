(ns sente-websockets-rabbitmq.html.index
  (:require [hiccup.core :refer [html]]
            [clojure.zip :as zip]))

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

(defn chat []
  (-> [:html
       [:head
        [:link {:href "css/style.css"
                :rel "stylesheet"
                :type "text/css"}]]
       [:body
        [:div {:id "app"}]
        [:script {:src "js/compiled/sente_websockets_rabbitmq.js"}]]]
      (add-headers basic-headers)
      (add-headers bootstrap-headers)
      html))

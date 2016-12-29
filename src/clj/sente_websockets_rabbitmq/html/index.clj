(ns sente-websockets-rabbitmq.html.index
  (:require [hiccup.core :refer [html]]))

(defn login []
  (html [:html
         [:head
          [:link {:rel "stylesheet"
                  :href "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css"
                  :integrity "sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7"
                  :crossorigin "anonymous"}]
          [:script {:src "https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js"}]
          [:script {:src "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js"
                    :integrity "sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS"
                    :crossorigin "anonymous"}]]
         [:body
          [:div {:class "app container"}
           [:h1 "A wild chat application appears"]
           [:p "Hi there! This is a pretty basic chat thing implemented in clojure/scirpt, websockets, and rabbitmq with authentication over oauth!"]
           [:a {:type "button" :class "btn btn-default" :href "/chat"} "login with google"]]]]))

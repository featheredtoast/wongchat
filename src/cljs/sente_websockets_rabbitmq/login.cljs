(ns sente-websockets-rabbitmq.login
  (:require [reagent.core :as reagent :refer [atom]]))

(enable-console-print!)

(defn login []
  [:div {:class "app container"}
   [:h1 "A wild chat application appears"]
   [:p "Hi there! This is a pretty basic chat thing implemented in clojure/scirpt, websockets, and rabbitmq with authentication over oauth!"]
   [:a {:type "button" :class "btn btn-default" :href "/chat"} "login with google"]])

(reagent/render [login] (js/document.getElementById "app"))

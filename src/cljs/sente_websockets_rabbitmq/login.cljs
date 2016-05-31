(ns sente-websockets-rabbitmq.login
  (:require [reagent.core :as reagent :refer [atom]]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"}))

(defn login []
  [:div {:class "app"}
   [:h1 (:text @app-state)]
   [:a {:href "/chat"} "login with google"]])

(reagent/render [login] (js/document.getElementById "app"))

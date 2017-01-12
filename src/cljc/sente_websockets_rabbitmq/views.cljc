(ns sente-websockets-rabbitmq.views
  #?(:cljs (:require-macros
            [cljs.core.async.macros :as asyncm :refer (go go-loop)]
            [reagent.ratom :refer [reaction]]))
  #?(:cljs
     (:require
      [reagent.core :as reagent :refer [atom]]
      [sente-websockets-rabbitmq.app :as core :refer [app-state submit-message input-change]])))

#?(:cljs
   (do
     (def app-messages (reaction (:messages @app-state)))
     (def app-typing (reaction (:typing @app-state)))
     (def app-input (reaction (:input @app-state)))
     (def app-user (reaction (:user @app-state)))
     (defn get-component [component]
       [component])
     (defn get-component-with-arg [component arg]
       [component arg]))
   
   :clj
   (do
     (def app-state (atom {}))
     (def app-messages (atom []))
     (def app-typing (atom #{}))
     (def app-user (atom "username here"))
     (defn get-component [component]
       (component))
     (defn get-component-with-arg [component arg]
       (component arg))
     (def app-input (atom "jaja"))
     (defn submit-message [] ())
     (defn input-change [] ())))

(defn print-message [message-key {:keys [uid msg] :as message}]
  ^{:key message-key} [:div (str uid ": " msg)])

(defn print-messages []
  [:div
   (map-indexed print-message @app-messages)])

(defn print-typing-notification-message [typists]
  (case (count typists)
    1 [:span (str (first typists) " is typing")]
    2 [:span (str (first typists) " and " (second typists) " are typing")]
    [:span "multiple people are typing"]))

(defn print-typing-notification [typists]
  [:div {:class "typing-notification-container"}
   (when (< 0 (count typists))
     [:span {:class "small typing-notification"}
      (get-component-with-arg print-typing-notification-message typists)
      [:div {:class "circle"}]
      [:div {:class "circle circle2"}]
      [:div {:class "circle circle3"}]])])

(defn print-typists []
  (let [typists @app-typing]
    (get-component-with-arg print-typing-notification typists)))

(defn main-app []
  [:div {:class "app"}
   [:nav {:class "navbar navbar-default"}
    [:div {:class "container-fluid"}
     [:span {:class "pull-right navbar-text"} [:span @app-user]
      " " [:a {:href "/logout"} "logout"]]]]
   [:div {:class "container"}
    [:div {:class "panel panel-default"}
     [:div {:class "panel-body"}
      [:div {:class "col-lg-12"}
       (get-component print-messages)
       (get-component print-typists)]]]
    
    [:div {:class "col-lg-4"}
     [:div {:class "input-group"}
      [:input {:class "form-control"
               :placeholder "type a message..."
               :type "text" :ref "message"
               :on-change input-change
               :disabled #?(:clj true
                            :cljs false)
               :on-key-press (fn [e]
                               (when (= 13 (.-charCode e))
                                 (submit-message)))
               :value @app-input}]
      [:span {:class "input-group-btn"}
       [:button {:class "btn btn-default" :on-click submit-message} "send"]]]]]])

#?(:clj (defn init-main-app [app-state]
          (def app-messages (atom (:messages @app-state)))
          (def app-typing (atom (:typing @app-state)))
          (def app-user (atom (:user @app-state)))
          (def app-input (atom ""))
          (defn submit-message [] ())
          (defn input-change [] ())
          (main-app)))

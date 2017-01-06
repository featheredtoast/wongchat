(ns sente-websockets-rabbitmq.views
  #?(:cljs (:require-macros
            [cljs.core.async.macros :as asyncm :refer (go go-loop)]))
  (:require [rum.core :as rum]
            #?(:cljs [sente-websockets-rabbitmq.app :as core :refer [app-state submit-message input-change]])))

#?(:cljs
   (do
     (def app-messages (rum/cursor-in app-state [:messages]))
     (def app-typing (rum/cursor-in app-state [:typing]))
     (def app-input (rum/cursor-in app-state [:input]))
     (def app-user (rum/cursor-in app-state [:user]))
     (defn gen-message-key []
       (.random js/Math)))
   
   :clj
   (do
     (def app-state (atom {}))
     (def app-messages (atom []))
     (def app-typing (atom #{}))
     (def app-user (atom "username here"))
     (def app-input (atom "jaja"))
     (defn gen-message-key []
       (rand-int 10000))
     (defn submit-message [] ())
     (defn input-change [] ())))

(defn print-message [{:keys [uid msg] :as message}]
  ^{:key (gen-message-key)} [:div (str uid ": " msg)])

(defn print-messages []
  (map print-message (rum/react app-messages)))

(defn print-typing-notification-message [typists]
  (case (count typists)
    1 [:span (str (first typists) " is typing")]
    2 [:span (str (first typists) " and " (second typists) " are typing")]
    [:span "multiple people are typing"]))

(defn print-typing-notification [typists]
  [:div {:class "typing-notification-container"}
   (when (< 0 (count typists))
     [:span {:class "small typing-notification"}
      (print-typing-notification-message typists)
      [:div {:class "circle"}]
      [:div {:class "circle circle2"}]
      [:div {:class "circle circle3"}]])])

(defn print-typists []
  (let [typists (rum/react app-typing)]
    (print-typing-notification typists)))

(rum/defc main-app < rum/reactive []
  [:div {:class "app"}
   [:nav {:class "navbar navbar-default"}
    [:div {:class "container-fluid"}
     [:span {:class "pull-right navbar-text"} [:span (rum/react app-user)]
      " " [:a {:href "/logout"} "logout"]]]]
   [:div {:class "container"}
    [:div {:class "panel panel-default"}
     [:div {:class "panel-body"}
      [:div {:class "col-lg-12"}
       (print-messages)
       (print-typists)]]]
    
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
               :value (rum/react app-input)}]
      [:span {:class "input-group-btn"}
       [:button {:class "btn btn-default" :on-click submit-message} "send"]]]]]])

#?(:clj (defn init-main-app [app-state]
          (def app-messages (atom (:messages @app-state)))
          (def app-typing (atom (:typing @app-state)))
          (def app-user (atom (:user @app-state)))
          (def app-input (atom ""))
          (defn gen-message-key []
            (rand-int 10000))
          (defn submit-message [] ())
          (defn input-change [] ())
          (main-app)))

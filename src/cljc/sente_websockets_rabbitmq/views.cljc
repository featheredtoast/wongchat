(ns sente-websockets-rabbitmq.views
  #?(:cljs (:require-macros
            [cljs.core.async.macros :as asyncm :refer (go go-loop)]))
  (:require
   [rum.core :as rum]
   #?(:cljs [sente-websockets-rabbitmq.app :as core :refer [app-state submit-message input-change]])))

#?(:cljs
   (do
     (def app-messages (rum/cursor-in app-state [:messages]))
     (def app-typing (rum/cursor-in app-state [:typing]))
     (def app-input (rum/cursor-in app-state [:input]))
     (def app-user (rum/cursor-in app-state [:user]))
     (def app-connected (rum/cursor-in app-state [:connected])))
   :clj
   (do
     (def app-messages (atom []))
     (def app-typing (atom false))
     (def app-user (atom ""))
     (def app-input (atom ""))
     (def app-connected (atom true))
     (defn submit-message [] ())
     (defn input-change [] ())))

(defn print-message [message-key {:keys [uid msg] :as message}]
  [:div {:key message-key} (str uid ": " msg)])

(rum/defc print-messages < rum/reactive []
  [:div
   (map-indexed print-message (rum/react app-messages))])

(rum/defc print-typing-notification-message < rum/reactive [typists]
  (case (count typists)
    1 [:span (str (first typists) " is typing")]
    2 [:span (str (first typists) " and " (second typists) " are typing")]
    [:span "multiple people are typing"]))

(rum/defc print-typing-notification < rum/reactive [typists]
  [:div {:class "typing-notification-container"}
   (when (< 0 (count typists))
     [:span {:class "small typing-notification"}
      (print-typing-notification-message typists)
      [:div {:class "circle"}]
      [:div {:class "circle circle2"}]
      [:div {:class "circle circle3"}]])])

(rum/defc print-typists < rum/reactive []
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
               :disabled (not (rum/react app-connected))
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
          (def app-connected (atom false))
          (defn submit-message [] ())
          (defn input-change [] ())
          (rum/render-html (main-app))))

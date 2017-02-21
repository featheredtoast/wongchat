(ns sente-websockets-rabbitmq.views
  #?(:cljs (:require-macros
            [cljs.core.async.macros :as asyncm :refer (go go-loop)]))
  (:require
   [rum.core :as rum]
   #?(:cljs [sente-websockets-rabbitmq.app :as core :refer [app-state submit-message input-change history-recall set-cursor-position swap-channel open-menu close-menu]])))

#?(:cljs
   (do
     (def app-messages (rum/cursor-in app-state [:messages]))
     (def app-typing (rum/cursor-in app-state [:typing]))
     (def app-input (rum/cursor-in app-state [:input]))
     (def app-user (rum/cursor-in app-state [:user]))
     (def app-connected (rum/cursor-in app-state [:connected]))
     (def app-active-channel (rum/cursor-in app-state [:active-channel]))
     (def app-menu-state (rum/cursor-in app-state [:menu]))
     (def input-change-mixin
       {:did-update
        (fn [state]
          (let [element (js/ReactDOM.findDOMNode (:rum/react-component state))]
            (set-cursor-position element))
          state)}))
   :clj
   (do
     (def app-messages (atom []))
     (def app-typing (atom false))
     (def app-user (atom ""))
     (def app-input (atom ""))
     (def app-connected (atom true))
     (def app-active-channel (atom ""))
     (def app-menu-state (atom {:px-open 0
                                :max-px-width 201}))
     (defn submit-message [] ())
     (defn history-recall [] ())
     (defn input-change [] ())
     (defn open-menu [] ())
     (defn close-menu [] ())
     (defn swap-channel [channel] ())
     (def input-change-mixin {})))

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

(rum/defc user-input < rum/reactive
  input-change-mixin
   []
  [:input {:class "form-control user-input"
           :placeholder "type a message..."
           :type "text" :ref "message"
           :on-change input-change
           :disabled (not (rum/react app-connected))
           :on-key-down (fn [e]
                          (when (some #(= % (aget e "keyCode")) [38 40])
                            (history-recall e)))
           :on-key-press (fn [e]
                           (when (= 13 (aget e "charCode"))
                             (submit-message)))
           :value (rum/react app-input)}])

(rum/defc channel-list < rum/reactive []
  (let [active-channel (rum/react app-active-channel)]
      [:ul {:class "nav nav-stacked nav-pills"}
       [:li (when (= active-channel "#general") {:class "active"}) [:a {:href "#" :on-click #(swap-channel "#general")} "#general"]]
       [:li (when (= active-channel "#random") {:class "active"}) [:a {:href "#" :on-click #(swap-channel "#random")} "#random"]]]))

(rum/defc main-menu < rum/reactive []
  (let [{:keys [px-open max-px-width] :as menu-state} (rum/react app-menu-state)
        menu-offset (- px-open max-px-width )
        opacity-range (* 0.5 (/ px-open max-px-width))]
    [:div {:class "visible-xs"}
     [:div
      {:class "main-menu panel"
       :style {:left menu-offset}}
      (channel-list)]
     [:div
      (if (= 0 px-open)
        {:class "content-mask content-mask-close"}
        {:class "content-mask"
         :style {:opacity opacity-range}
         :on-click close-menu})]]))

(rum/defc main-app < rum/reactive []
  [:div {:class "app"}
   (main-menu)
   [:nav {:class "navbar navbar-default"}
    [:div {:class "container-fluid"}
     [:button {:type "button" :class "pull-left hidden-lg navbar-toggle"
             :on-click open-menu}
      [:span {:class "icon-bar"}]
      [:span {:class "icon-bar"}]
      [:span {:class "icon-bar"}]]
     [:span {:class "pull-right navbar-text"} [:span (rum/react app-user)]
      " " [:a {:href "/logout"} "logout"]]]]
   [:div {:class "col-sm-2 hidden-xs"}
    (channel-list)]
   [:div {:class "col-sm-10"}
    [:div {:class "panel panel-default"}
     [:div {:class "panel-body"}
      [:div {:class "col-sm-12"}
       (print-messages)
       (print-typists)]]]

    [:div {:class "col-lg-12"}
     [:div {:class "input-group"}
      (user-input)
      [:span {:class "input-group-btn"}
       [:button {:class "btn btn-default" :on-click submit-message} "send"]]]]]])

#?(:clj (defn init-main-app [app-state]
          (def app-messages (atom (:messages @app-state)))
          (def app-typing (atom (:typing @app-state)))
          (def app-user (atom (:user @app-state)))
          (def app-input (atom ""))
          (def app-connected (atom false))
          (def app-active-channel (atom (:active-channel @app-state)))
          (def app-menu-state (atom (:menu @app-state)))
          (defn submit-message [] ())
          (defn input-change [] ())
          (rum/render-html (main-app))))

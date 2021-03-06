(ns wongchat.views
  (:require
   [rum.core :as rum]
   #?(:cljs [wongchat.core :as core :refer [app-state submit-message input-change history-recall set-cursor-position swap-channel open-menu close-menu subscribe unsubscribe]])))

(defn init-vars [app-state]
  (def app-messages (rum/cursor-in app-state [:messages]))
  (def app-typing (rum/cursor-in app-state [:typing]))
  (def app-input (rum/cursor-in app-state [:input]))
  (def app-user (rum/cursor-in app-state [:user]))
  (def app-connected (rum/cursor-in app-state [:connected]))
  (def app-active-channel (rum/cursor-in app-state [:active-channel]))
  (def app-menu-state (rum/cursor-in app-state [:menu]))
  (def network-connected (rum/cursor-in app-state [:network-up?]))
  (def app-subscribed (rum/cursor-in app-state [:subscribed?])))

#?(:cljs
   (do
     (init-vars app-state)
     (def input-change-mixin
       {:did-update
        (fn [state]
          (let [element (js/ReactDOM.findDOMNode (:rum/react-component state))]
            (set-cursor-position element))
          state)}))
   :clj
   (do
     (declare main-app
              app-messages
              app-typing
              app-user
              app-input
              app-connected
              app-active-channel
              app-menu-state
              network-connected
              app-subscribed
              submit-message
              history-recall
              input-change
              open-menu
              close-menu
              subscribe
              unsubscribe
              swap-channel
              input-change-mixin)
     (defn init-main-app [app-state]
       (init-vars app-state)
       (rum/render-html (main-app)))))

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
  [:div.typing-notification-container
   (when (< 0 (count typists))
     [:span.small.typing-notification
      (print-typing-notification-message typists)
      [:.circle]
      [:.circle.circle2]
      [:.circle.circle3]])])

(rum/defc print-typists < rum/reactive []
  (let [typists (rum/react app-typing)]
    (print-typing-notification typists)))

(rum/defc user-input < rum/reactive
  input-change-mixin
   []
  [:input.form-control.user-input {
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
      [:ul.nav.nav-stacked.nav-pills
       [:li (when (= active-channel "#general") {:class "active"}) [:a {:href "/chat/channel/general"} "#general"]]
       [:li (when (= active-channel "#random") {:class "active"}) [:a {:href "/chat/channel/random"} "#random"]]]))

(rum/defc main-menu < rum/reactive []
  (let [{:keys [px-open max-px-width] :as menu-state} (rum/react app-menu-state)
        menu-offset (- px-open max-px-width )
        opacity-range (* 0.5 (/ px-open max-px-width))]
    [:.visible-xs
     [:.main-menu.panel
      {:style {:left menu-offset}}
      (channel-list)]
     [:div
      (if (= 0 px-open)
        {:class "content-mask content-mask-close"}
        {:class "content-mask"
         :style {:opacity opacity-range}
         :on-click close-menu})]]))

(rum/defc network-down-banner < rum/reactive []
  (let [network-up? (rum/react network-connected)]
    (if network-up?
      [:div ]
      [:.alert.alert-danger "No network connection"])))

(rum/defc main-app < rum/reactive []
  [:.app
   (main-menu)
   [:nav.navbar.navbar-default
    [:.container-fluid
     [:button.pull-left.hidden-lg.navbar-toggle {:type "button"
             :on-click open-menu}
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:span.pull-right.navbar-text [:span (rum/react app-user)]
      (if (rum/react app-subscribed)
        [:button.btn.btn-default {:on-click unsubscribe} "Un-Subscribe!"]
        [:button.btn.btn-default {:on-click subscribe} "Subscribe!"])
      " " [:a {:href "/logout"} "logout"]]]]
   [:.col-sm-2.hidden-xs
    (channel-list)]
   [:.col-sm-10
    (network-down-banner)
    [:.panel.panel-default
     [:.panel-body
      [:.col-sm-12
       (print-messages)
       (print-typists)]]]

    [:.col-lg-12
     [:.input-group
      (user-input)
      [:span {:class "input-group-btn"}
       [:button.btn.btn-default {:on-click submit-message} "send"]]]]]])

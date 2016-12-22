(ns sente-websockets-rabbitmq.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [com.stuartsierra.component :as component]
            [cljs.core.async :as async :refer (<! >! <! poll! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [system.components.sente :refer [new-channel-socket-client]]
            [org.clojars.featheredtoast.reloaded-repl-cljs :as reloaded]))

(enable-console-print!)

(defonce app-state (atom {:messages []
                          :typing #{}
                          :user-typing false
                          :input ""}))

(defonce message-chan (chan))

(defn chat-init [chsk-send!]
  (chsk-send!
   [:chat/init] ;event
   8000 ; timeout
   (fn [reply])))

(defn send-message [chsk-send! msg type]
  (chsk-send!
   [type {:msg msg}] ;event
   8000 ; timeout
   (fn [reply])))

(defn start-message-sender [chsk-send!]
  (go-loop []
    (let [{:keys [msg type]} (<! message-chan)]
      (if (= type :shutdown)
        (println "shutting down message sender")
        (do
          (println "sending message... " msg)
          (send-message chsk-send! msg type)
          (recur))))))

(defn handle-message [{:keys [msg uid]}]
  (let [new-value-uncut (->> (str uid ": " msg)
                             (conj (:messages @app-state)))
        new-value-count (count new-value-uncut)
        limit 10
        new-value-offset (or (and (< 0 (- new-value-count limit)) limit) new-value-count)
        new-value-cut (- new-value-count new-value-offset)
        new-value (subvec new-value-uncut new-value-cut)]
    (swap! app-state assoc :messages new-value)))

(defn handle-init [payload]
  (let [messages (mapv #(str (:uid %) ": " (:msg %)) payload)]
    (swap! app-state assoc :messages messages)))

(defn handle-typing [{:keys [uid msg]}]
  (let [typists (:typing @app-state)
        new-typists(if msg
          (conj typists uid)
          (disj typists uid))]
    (swap! app-state assoc :typing new-typists))
  (println "typing notification by " uid " and it is " msg))

(defmulti event-msg-handler (fn [_ msg] (:id msg))) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [chsk-send! {:as ev-msg :keys [id ?data event]}]
  (event-msg-handler chsk-send! ev-msg))
(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [_ {:as ev-msg :keys [event]}]
    (println "Unhandled event: %s" event))

  (defmethod event-msg-handler :chsk/state
    [_ {:as ev-msg :keys [?data]}]
    (if (= ?data {:first-open? true})
      (println "Channel socket successfully established!")
      (println "Channel socket state change: %s" ?data)))

  (defmethod event-msg-handler :chsk/recv
    [_ {:as ev-msg :keys [?data]}]
    (println "Push event from server: %s" ?data)
    (let [data-map (apply array-map ?data)
          payload (:chat/message data-map)
          init-payload (:chat/init data-map)
          typing (:chat/typing data-map)]
      (or (nil? payload) (handle-message payload))
      (or (nil? init-payload) (handle-init init-payload))
      (or (nil? typing) (handle-typing typing))))

  (defmethod event-msg-handler :chsk/handshake
    [chsk-send! {:as ev-msg :keys [?data]}]
    (let [[?uid ?csrf-token ?handshake-data] ?data]
      (do (println "Handshake: %s" ?data)
          (chat-init chsk-send!))))

  ;; Add your (defmethod handle-event-msg! <event-id> [ev-msg] <body>)s here...
  )

(defrecord MessageSendHandler []
  component/Lifecycle
  (start [component]
    component)
  (stop [component]
    (println "sending shutdown message!")
    (put! message-chan {:type :shutdown})
    component))
(defn new-message-send-handler []
  (map->MessageSendHandler {}))

(defrecord SenteHandler [router chsk sente]
  component/Lifecycle
  (start [component]
    (let [{:keys [chsk-send! chsk ch-chsk]} sente]
      (chat-init chsk-send!)
      (start-message-sender chsk-send!)
      (assoc component
             :chsk chsk
             :router
             (sente/start-chsk-router! ch-chsk (partial event-msg-handler* chsk-send!)))))
  (stop [component]
    (when chsk
      (println "disconnecting...")
      (sente/chsk-disconnect! chsk))
    (when-let [stop-f router]
      (println "stopping router...") 
      (stop-f))
    (assoc component
           :chsk nil
           :router nil)))

(defn new-sente-handler []
  (map->SenteHandler {}))

(defn chat-system []
  (component/system-map
   :sente (new-channel-socket-client)
   :sente-handler (component/using
                   (new-sente-handler)
                   [:sente])
   :message-sender (new-message-send-handler)))

(reloaded/set-init-go! #(chat-system))

(defn send-typing-notification [is-typing]
  (if (not= is-typing (:user-typing @app-state))
    (do
      (swap! app-state assoc :user-typing is-typing)
      (put! message-chan {:type :chat/typing :msg is-typing}))))

(defn input-change [e]
  (let [input (-> e .-target .-value)]
       (send-typing-notification (not= input ""))
       (swap! app-state assoc :input input)))

(defn gen-message-key []
  (.random js/Math))

(defn print-message [message]
  ^{:key (gen-message-key)} [:div message])

(defn print-typing-notification-message [typists]
  (case (count typists)
    1 [:span (str (first typists) " is typing")]
    2 [:span (str (first typists) " and " (second typists) " are typing")]
    [:span "multiple people are typing"]))

(defn get-cookie-map []
  (->> (map #(.split % "=") (.split (.-cookie js/document) #";"))
     (map vec)
     (map (fn [key-val] [(keyword (.trim (first key-val))) (.trim (second key-val))]))
     (map (partial apply hash-map))
     (apply merge)))

(defn submit-message []
  (when-let [msg (and (not= "" (:input @app-state)) (:input @app-state))]
    (put! message-chan {:type :chat/submit :msg msg}))
  (send-typing-notification false)
  (swap! app-state assoc :input ""))

(defn main-app []
  [:div {:class "app"}
   [:nav {:class "navbar navbar-default"}
    [:div {:class "container-fluid"}
     [:span {:class "pull-right navbar-text"} [:span (js/decodeURIComponent (:user (get-cookie-map)))] " " [:a {:href "/logout"} "logout"]]]]
   [:div {:class "container"}
    [:div {:class "panel panel-default"}
     [:div {:class "panel-body"}
      [:div {:class "col-lg-12"}
       (map print-message (:messages @app-state))]]]
    
    [:div {:class "col-lg-4"}
     [:div {:class "input-group"}
      [:input {:class "form-control"
               :placeholder "type a message..."
               :type "text" :ref "message"
               :on-change input-change
               :on-key-press (fn [e]
                               (when (= 13 (.-charCode e))
                                 (submit-message)))
               :value (:input @app-state)}]
      [:span {:class "input-group-btn"}
       [:button {:class "btn btn-default" :on-click submit-message} "send"]]]]
    [:div {:class "col-lg-12"}
     (let [typists (:typing @app-state)]
       (when (< 0 (count typists))
         [:span {:class "small typing-notification"}
          (print-typing-notification-message typists)
          [:div {:class "circle"}]
          [:div {:class "circle circle2"}]
          [:div {:class "circle circle3"}]]))]]])

(reagent/render [main-app] (js/document.getElementById "app"))

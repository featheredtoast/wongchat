(ns sente-websockets-rabbitmq.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [com.stuartsierra.component :as component]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [system.components.sente :refer [new-channel-socket-client]]))

(enable-console-print!)

(defonce app-state (atom {:messages []
                          :typing #{}
                          :user-typing false
                          :online-users #{}
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
        (println "shutdown")
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

(defn handle-init [{:as payload :keys [recent-messages online-users]}]
  (let [messages (mapv #(str (:uid %) ": " (:msg %)) recent-messages)]
    (swap! app-state assoc :messages messages)
    (swap! app-state assoc :online-users online-users)))

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
                   [:sente])))

(defonce app-system (atom nil))

(defn init []
  (reset! app-system
    (chat-system)))

(defn start []
  (swap! app-system component/start))

(defn stop []
  (swap! app-system
    (fn [s] (when s (component/stop s)))))
 
(defn run []
  (init)
  (start))

(when (= nil @app-system) (run))

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

(defn print-typists [typist]
  ^{:key (gen-message-key)} [:span {:class "small"} typist " is typing"])

(defn print-user [user]
  ^{:key (gen-message-key)} [:ul {:class "list-group-item"} user])

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
    [:div {:class "col-lg-10"}
     [:div {:class "panel panel-default"}
      [:div {:class "panel-body"}
       [:div {:class "col-lg-12"}
        (map print-message (:messages @app-state))]]]]
    [:div {:class "col-lg-2"}
     [:span "Online"]
     [:ul {:class "list-group"} (map print-user (:online-users @app-state))]]
    
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
     (map print-typists (:typing @app-state))]]])

(reagent/render [main-app] (js/document.getElementById "app"))

(defn on-figwheel-reload []
  (println "reloading...")
  (stop)
  (put! message-chan {:type :shutdown})
  (run))

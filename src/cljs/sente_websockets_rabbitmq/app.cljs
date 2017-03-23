(ns sente-websockets-rabbitmq.app
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [com.stuartsierra.component :as component]
            [cljs.core.async :as async :refer (<! >! <! poll! put! chan timeout)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [system.components.sente :refer [new-channel-socket-client]]
            [sente-websockets-rabbitmq.data :refer [serialize deserialize]]
            [cljsjs.hammer]
            [goog.dom :as dom]
            [goog.events :as events]
            [goog.events.OnlineHandler]))

(enable-console-print!)

(defonce message-chan (chan))

(defonce history-chan (chan))

(defonce app-state (atom
                    (deserialize (aget (dom/getElement "initial-state") "textContent"))))

(defn send-message [chsk-send! message type]
  (chsk-send!
   [type message] ;event
   8000 ; timeout
   (fn [reply])))

(defn empty-channel! [channel]
  (let [msg (poll! channel)]
    (when msg (empty-channel! channel))))

(defn start-message-sender [chsk-send!]
  (println "starting message sender...")
  (empty-channel! message-chan)
  (go-loop []
    (let [{:keys [type channel] :as message} (<! message-chan)]
      (if (= type :shutdown)
        (println "shutting down message sender")
        (do
          (println "sending message... " message " to channel " channel)
          (send-message chsk-send! message type)
          (recur))))))

(defn swap-channel [channel]
  (go
    (swap! app-state assoc :active-channel channel)
    (when (= nil (get-in @app-state [:channel-data channel]))
      (>! message-chan {:type :chat/history :channel channel})
      (let [messages (<! history-chan)]
        (swap! app-state assoc-in [:channel-data channel] {:messages messages :typing #{}})))
    (swap! app-state assoc :messages (get-in @app-state [:channel-data channel :messages]))
    (swap! app-state assoc :typing (get-in @app-state [:channel-data channel :typing]))))

(defn handle-message [{:keys [msg uid channel] :as message}]
  (when (not= nil (get-in @app-state [:channel-data channel]))
    (let [new-value-uncut (conj (get-in @app-state [:channel-data channel :messages]) (dissoc message :channel))
          new-value-count (count new-value-uncut)
          limit 10
          new-value-offset (or (and (< 0 (- new-value-count limit)) limit) new-value-count)
          new-value-cut (- new-value-count new-value-offset)
          new-value (subvec new-value-uncut new-value-cut)]
      (swap! app-state assoc-in [:channel-data channel :messages] new-value)
      (when (= channel (:active-channel @app-state))
        (swap! app-state assoc :messages new-value)))))

(defn handle-typing [{:keys [uid msg channel] :as message}]
  (when (not= nil (get-in @app-state [:channel-data channel]))
    (let [typists (get-in @app-state [:channel-data channel :typing])
          new-typists (if msg
                        (conj typists uid)
                        (disj typists uid))]
      (swap! app-state assoc-in [:channel-data channel :typing] new-typists)
      (when (= channel (:active-channel @app-state))
        (swap! app-state assoc :typing new-typists)))))

(defn handle-history [messages]
  (println "handling history: " messages)
  (put! history-chan messages))

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
    (when-let [new-state (second ?data)]
      (swap! app-state assoc :connected (:open? new-state))
      (if (:first-open? new-state)
        (println "Channel socket successfully established!")
        (println "Channel socket state change: %s" ?data))))

  (defmethod event-msg-handler :chsk/recv
    [_ {:as ev-msg :keys [?data]}]
    (let [data-map (apply array-map ?data)
          payload (:chat/message data-map)
          typing (:chat/typing data-map)
          history (:chat/history data-map)]
      (or (nil? payload) (handle-message payload))
      (or (nil? typing) (handle-typing typing))
      (or (nil? history) (handle-history history))))

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

(defn send-typing-notification [is-typing]
  (if (not= is-typing (:user-typing @app-state))
    (do
      (swap! app-state assoc :user-typing is-typing)
      (put! message-chan {:type :chat/typing :msg is-typing :channel (:active-channel @app-state)}))))

(defn input-change [e]
  (let [input (aget e "target" "value")]
    (send-typing-notification (not= input ""))
    (swap! app-state assoc :input input)
    (swap! app-state assoc :latest-input input)
    (swap! app-state assoc :message-history-position 0)
    (swap! app-state assoc :reset-cursor false)))

(defn history-recall-back []
  (when (< (:message-history-position @app-state) (count (:message-history @app-state)))
    (let [input (nth (:message-history @app-state) (:message-history-position @app-state))]
      (swap! app-state assoc :input input)
      (swap! app-state assoc :message-history-position (inc (:message-history-position @app-state))))))

(defn history-recall-forward []
  (when (< 0 (:message-history-position @app-state))
    (swap! app-state assoc :message-history-position (dec (:message-history-position @app-state))))
  (if (< 0 (:message-history-position @app-state))
    (do
      (let [input (nth (:message-history @app-state) (dec (:message-history-position @app-state)))]
        (swap! app-state assoc :input input)))
    (if (= 0 (:message-history-position @app-state))
      (let [input (:latest-input @app-state)]
        (swap! app-state assoc :input input)))))

(defn set-cursor-position [element]
  (when (:reset-cursor @app-state)
    (.setSelectionRange element 0 0)
    (swap! app-state assoc :reset-cursor false)))

(defn history-recall [e]
  (let [element (aget e "target")
        cursor-position (aget element "selectionStart")
        input (aget element "value")]
    (when (and (= 0 cursor-position) (= 38 (aget e "keyCode")))
      (history-recall-back)
      (swap! app-state assoc :reset-cursor true))
    (when (and (= (count input) cursor-position) (= 40 (aget e "keyCode")))
      (history-recall-forward)
      (swap! app-state assoc :reset-cursor false))))

(defn submit-message []
  (when-let [msg (and (not= "" (:input @app-state)) (:input @app-state))]
    (put! message-chan {:type :chat/message :msg msg :channel (:active-channel @app-state)})
    (swap! app-state assoc :message-history (conj (:message-history @app-state) msg))
    (swap! app-state assoc :message-history-position 0))
  (send-typing-notification false)
  (swap! app-state assoc :input "")
  (swap! app-state assoc :latest-input ""))

(def last-swipe-event (atom {:direction :close
                             :velocity 0
                             :distance 0
                             :end? true
                             :start-x 0}))

(defn open-menu []
  (go-loop [px-open (get-in @app-state [:menu :px-open])
            velocity-open 8
            ticks 0]
    (let [max-px-width (get-in @app-state [:menu :max-px-width])
          new-px-open (min max-px-width (+ velocity-open px-open))]
      (when (< px-open max-px-width)

        (swap! app-state assoc-in [:menu :px-open] new-px-open)
        (<! (timeout 1))
        (recur new-px-open (max 4 (- velocity-open (/ ticks 5))) (inc ticks))))))

(defn close-menu []
  (go-loop [px-open (get-in @app-state [:menu :px-open])
            velocity-close 8
            ticks 0]
    (let [max-px-width (get-in @app-state [:menu :max-px-width])
          new-px-open (max 0 (- px-open velocity-close))]
      (when (< 0 px-open)
        (swap! app-state assoc-in [:menu :px-open] new-px-open)
        (<! (timeout 1))
        (recur new-px-open (max 4 (- velocity-close (/ ticks 5))) (inc ticks))))))

(defn swipe-open-menu [e]
  (let [velocity (.abs js/Math (aget e "velocityX"))
        distance (.abs js/Math (aget e "deltaX"))
        delta-distance (.abs js/Math (- distance (:distance @last-swipe-event)))
        end? (:end? @last-swipe-event)
        start-x (:start-x @last-swipe-event)
        {:keys [px-open max-px-width]} (:menu @app-state)]
    (when (or (< 0 start-x 80) (not end?))
      (reset! last-swipe-event {:direction :open
                                :velocity velocity
                                :distance distance
                                :end? false
                                :start-x start-x})
      (swap! app-state assoc-in [:menu :px-open] (min max-px-width (+ px-open delta-distance))))))

(defn swipe-close-menu [e]
  (let [velocity (.abs js/Math (aget e "velocityX"))
        distance(.abs js/Math (aget e "deltaX"))
        delta-distance (.abs js/Math (- distance (:distance @last-swipe-event)))
        {:keys [px-open max-px-width]} (:menu @app-state)]
    (reset! last-swipe-event {:direction :close
                              :velocity velocity
                              :distance distance
                              :end? false
                              :start-x (:start-x @last-swipe-event)})
    (swap! app-state assoc-in [:menu :px-open] (max 0 (- px-open delta-distance)))))

(defn swipe-start [e]
  (swap! last-swipe-event assoc :start-x (aget e "center" "x")))

(defn swipe-end [e]
  (let [{:keys [direction velocity distance]} @last-swipe-event
        px-open (get-in @app-state [:menu :px-open])]
    (if (= direction :close)
      (cond (< 0.2 velocity) (close-menu)
            (< px-open 40) (close-menu)
            :else (open-menu))

      (cond (and (< 0.2 velocity) (< (:start-x @last-swipe-event) 80)) (open-menu)
            (< 60 px-open) (open-menu)
            :else (close-menu))))
  (reset! last-swipe-event {:direction :close
                            :velocity 0
                            :distance 0
                            :end? true
                            :start-x 0}))

(defn setup-swipe-events [ele]
  (doto (js/Hammer. ele)
    (.on "panright" swipe-open-menu)
    (.on "panleft" swipe-close-menu)
    (.on "panend" swipe-end)
    (.on "panstart" swipe-start)))

(defn keydown-focus []
  (.focus (dom/getElementByClass "user-input")))

(defn stop-hammer [hammer]
  (.destroy hammer))

(defn start-focus-listener []
  (events/listen
   (aget js/document "body")
   goog.events.EventType.KEYDOWN
   keydown-focus))

(defn stop-focus-listener []
  (events/removeAll
   (aget js/document "body")
   goog.events.EventType.KEYDOWN))

(defrecord EventHandler [hammer]
  component/Lifecycle
  (start [component]
    (println "starting event handler component...")
    (start-focus-listener)
    (assoc component :hammer (setup-swipe-events (aget js/document "body"))))
  (stop [component]
    (println "stopping event handler component...")
    (stop-hammer hammer)
    (stop-focus-listener)
    (dissoc component :hammer)))
(defn new-event-handler []
  (map->EventHandler {}))

(defn handle-online [chsk]
  (swap! app-state assoc :network-up? true)
  (sente/chsk-reconnect! chsk))

(defn handle-offline []
  (swap! app-state assoc :network-up? false))

(defn start-online-handler [sente]
  (doto (goog.events.OnlineHandler.)
    (.listen goog.events.EventType.OFFLINE handle-offline)
    (.listen goog.events.EventType.ONLINE #(handle-online (:chsk sente)))))

(defn stop-online-handler [handler]
  (.dispose handler))

(defrecord OnlineHandler [sente online-handler]
  component/Lifecycle
  (start [component]
    (let [online-handler (start-online-handler sente)]
      (assoc component :online-handler online-handler)))
  (stop [component]
    (stop-online-handler online-handler)
    (dissoc component :online-handler)))
(defn new-online-handler []
  (map->OnlineHandler {}))

(defn sw-register [registration]
  (println "registration successful with scope: " (aget registration "scope")))
(defn sw-error [err]
  (println "registration failed: " err))
(defrecord ServiceWorker []
  component/Lifecycle
  (start [component]
    (when-let [sw (aget js/navigator "serviceWorker")]
      (println "starting service worker...")
      (-> sw
          (.register "/sw.js")
          (.then sw-register)
          (.catch sw-error)))
    component)
  (stop [component]
    component))
(defn new-service-worker []
  (map->ServiceWorker {}))

(defn chat-system []
  (component/system-map
   :sente (new-channel-socket-client)
   :sente-handler (component/using
                   (new-sente-handler)
                   [:sente])
   :message-sender (new-message-send-handler)
   :event-handler (new-event-handler)
   :online-event-handler (component/using
                          (new-online-handler)
                          [:sente])
   :service-worker (new-service-worker)))

(when (:initializing @app-state)
  (swap! app-state assoc :initializing false))

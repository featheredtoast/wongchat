(ns sente-websockets-rabbitmq.app
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [com.stuartsierra.component :as component]
            [cljs.core.async :as async :refer (<! >! <! poll! put! chan timeout)]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [system.components.sente :refer [new-channel-socket-client]]
            [sente-websockets-rabbitmq.data :refer [serialize deserialize]]
            [cljsjs.hammer]))

(enable-console-print!)

(defonce message-chan (chan))

(defonce history-chan (chan))

(defonce app-state (atom
                    (deserialize (.html (js/$ "#initial-state")))))

(defn send-message [chsk-send! message type]
  (chsk-send!
   [type message] ;event
   8000 ; timeout
   (fn [reply])))

(defn start-message-sender [chsk-send!]
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
            velocity-open 5
            ticks 0]
    (let [max-px-width (get-in @app-state [:menu :max-px-width])
          new-px-open (min max-px-width (+ velocity-open px-open))]
      (when (< px-open max-px-width)

        (swap! app-state assoc-in [:menu :px-open] new-px-open)
        (<! (timeout 1))
        (recur new-px-open (max 3 (- velocity-open (/ ticks 5))) (inc ticks))))))

(defn close-menu []
  (go-loop [px-open (get-in @app-state [:menu :px-open])
            velocity-close 5
            ticks 0]
    (let [max-px-width (get-in @app-state [:menu :max-px-width])
          new-px-open (max 0 (- px-open velocity-close))]
      (when (< 0 px-open)
        (swap! app-state assoc-in [:menu :px-open] new-px-open)
        (<! (timeout 1))
        (recur new-px-open (max 3 (- velocity-close (/ ticks 5))) (inc ticks))))))

(defn swipe-open-menu [velocity distance]
  (let [delta-distance (.abs js/Math (- distance (:distance @last-swipe-event)))
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

(defn swipe-close-menu [velocity distance]
  (let [delta-distance (.abs js/Math (- distance (:distance @last-swipe-event)))
        {:keys [px-open max-px-width]} (:menu @app-state)]
    (reset! last-swipe-event {:direction :close
                              :velocity velocity
                              :distance distance
                              :end? false
                              :start-x (:start-x @last-swipe-event)})
    (swap! app-state assoc-in [:menu :px-open] (max 0 (- px-open delta-distance)))))

(defn swipe-start [start-x]
  (swap! last-swipe-event assoc :start-x start-x))

(defn swipe-end []
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
  (let [hammer (js/Hammer. ele)]
    (.on hammer "panright" (fn [e]
                             (swipe-open-menu (.abs js/Math (aget e "velocityX")) (.abs js/Math (aget e "deltaX")))))
    (.on hammer "panleft" (fn [e] (swipe-close-menu (.abs js/Math (aget e "velocityX")) (.abs js/Math (aget e "deltaX")))))
    (.on hammer "panend" (fn [e]
                           (swipe-end)))
    (.on hammer "panstart" (fn [e]
                             (println "panstart3333333333!!!")
                             (swipe-start (aget e "center" "x"))))
    hammer))

(defn keydown-focus [e]
  (println "moar focus 333333333333")
  (.focus (js/$ ".user-input")))

(defrecord EventHandler [swipes]
  component/Lifecycle
  (start [component]
    (println "starting event handler component...")
    (.addEventListener js/document "keydown"
                       keydown-focus false)
    (assoc component :swipes {:hammer (setup-swipe-events (aget js/document "body"))}))
  (stop [component]
    (println "stopping event handler component...")
    (let [hammer (:hammer swipes)]
      (println hammer)
      (.destory hammer)
      (println "stopping event listener...")
      (.removeEventListener js/document "keydown" keydown-focus)
      component)))
(defn new-event-handler []
  (map->EventHandler {}))

(defn chat-system []
  (component/system-map
   :sente (new-channel-socket-client)
   :sente-handler (component/using
                   (new-sente-handler)
                   [:sente])
   :message-sender (new-message-send-handler)
   :event-handler (new-event-handler)))

(when (:initializing @app-state)
  (swap! app-state assoc :initializing false))

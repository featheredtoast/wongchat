(ns sente-websockets-rabbitmq.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :input ""}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))
(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [{:as ev-msg :keys [event]}]
    (println "Unhandled event: %s" event))

  (defmethod event-msg-handler :chsk/state
    [{:as ev-msg :keys [?data]}]
    (if (= ?data {:first-open? true})
      (println "Channel socket successfully established!")
      (println "Channel socket state change: %s" ?data)))

  (defmethod event-msg-handler :chsk/recv
    [{:as ev-msg :keys [?data]}]
    (println "Push event from server: %s" ?data)
    (let [i-value (:i (:some/broadcast (apply array-map ?data)))]
      (println "i value %s" i-value)
      (swap! app-state assoc :text (str i-value))))

  (defmethod event-msg-handler :chsk/handshake
    [{:as ev-msg :keys [?data]}]
    (let [[?uid ?csrf-token ?handshake-data] ?data]
      (println "Handshake: %s" ?data)))

  ;; Add your (defmethod handle-event-msg! <event-id> [ev-msg] <body>)s here...
  )

(sente/start-chsk-router! ch-chsk event-msg-handler*)
(sente/chsk-reconnect! chsk)

(defn do-a-push [msg]
  (chsk-send!
   [:some/request-id {:msg msg}] ;event
   8000 ; timeout
   (fn [reply]
     (if (sente/cb-success? reply)
       (println "message sent")
       (println "message failed to send")))))

(defn input-change [e]
  (swap! app-state assoc :input (-> e .-target .-value))
  (swap! app-state assoc :text (-> e .-target .-value)))

(defn greeting []
  [:div {:class "app"}
   [:h1 (:text @app-state)]
   [:button {:on-click (partial do-a-push (:input @app-state))} "click"]
   [:input {:type "text" :ref "message"
            :on-change input-change
            :value (:input @app-state)}]])

(reagent/render [greeting] (js/document.getElementById "app"))

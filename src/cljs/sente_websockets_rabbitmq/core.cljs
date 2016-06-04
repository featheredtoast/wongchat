(ns sente-websockets-rabbitmq.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"
                          :messages ["messages appear here..." "hi message" "hi"]
                          :input ""}))

(defn start-ws! []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                    {:type :auto ; e/o #{:auto :ajax :ws}
                                     })]
    (def chsk       chsk)
    (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
    (def chsk-send! send-fn) ; ChannelSocket's send API fn
    (def chsk-state state)   ; Watchable, read-only atom
    ))

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
    (let [payload (:some/broadcast (apply array-map ?data))
          msg (:msg payload)
          sender (:sender payload)
          new-value-uncut (->> (str sender ": " msg)
                               (conj (:messages @app-state)))
          new-value-count (count new-value-uncut)
          limit 8
          new-value-offset (or (and (< 0 (- new-value-count limit)) limit) new-value-count)
          new-value-cut (- new-value-count new-value-offset)
          new-value (subvec new-value-uncut new-value-cut)]
      
      (swap! app-state assoc :messages new-value)))

  (defmethod event-msg-handler :chsk/handshake
    [{:as ev-msg :keys [?data]}]
    (let [[?uid ?csrf-token ?handshake-data] ?data]
      (println "Handshake: %s" ?data)))

  ;; Add your (defmethod handle-event-msg! <event-id> [ev-msg] <body>)s here...
  )

(defonce start! 
  (delay
   (start-ws!)
   (sente/start-chsk-router! ch-chsk event-msg-handler*)
   (sente/chsk-reconnect! chsk)))

(force start!)

(defn do-a-push [msg]
  (chsk-send!
   [:some/request-id {:msg msg}] ;event
   8000 ; timeout
   (fn [reply])))

(defn input-change [e]
  (swap! app-state assoc :input (-> e .-target .-value))
  (swap! app-state assoc :text (-> e .-target .-value)))

(defn gen-message-key []
  (.random js/Math))

(defn print-message [message]
  ^{:key (gen-message-key)} [:div message])

(defn get-cookie-map []
  (->> (map #(.split % "=") (.split (.-cookie js/document) #";"))
     (map vec)
     (map (fn [key-val] [(keyword (.trim (first key-val))) (.trim (second key-val))]))
     (map (partial apply hash-map))
     (apply merge)))

(defn main-app []
  [:div {:class "app"}
   [:nav {:class "navbar navbar-default"}
    [:div {:class "container-fluid"}
     [:div {:class "navbar-right"}
      [:span {:class "navbar-text"} [:span (js/decodeURIComponent (:user (get-cookie-map)))] [:a {:href "/logout"} " logout"]]]]]
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
               :value (:input @app-state)}]
      [:span {:class "input-group-btn"}
       [:button {:class "btn btn-default" :on-click (partial do-a-push (:input @app-state))} "send"]]]]]])

(reagent/render [main-app] (js/document.getElementById "app"))

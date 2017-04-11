(ns wongchat.events
  (:require
   [langohr.queue     :as lq]
   [langohr.consumers :as lc]
   [langohr.basic     :as lb]
   [wongchat.data :refer [serialize deserialize]]
   [com.stuartsierra.component :as component]
   [wongchat.db :as db]
   [wongchat.web-push :as web-push]
   [wongchat.config :refer [config]]
   [wongchat.router :as router]
   [bidi.bidi :as bidi]))

(defn stringify-keyword [keywd]
  (str (namespace keywd) "/" (name keywd)))

(defn publish [rabbit-data msg type]
  "publish an event to rabbit in json. rabbit-data is a map of {:ch :qname}"
  (lb/publish (:ch rabbit-data) "" (:qname rabbit-data) (serialize msg) {:content-type "text/json" :type (stringify-keyword type)}))

(defn do-web-push [msg]
  (let [subscriptions (db/get-subscriptions)]
    (future
      (dorun
       (for [subscription subscriptions]
         (try
           (web-push/do-push! (db/get-server-credentials) (:subscription subscription) "test@test.com" (serialize msg))
           (catch clojure.lang.ExceptionInfo e
             (println "invalid subscription: " (:subscription subscription))
             (db/delete-subscription (:id subscription)))))))))

(defmulti event-msg-handler (fn [_ msg] (:id msg))) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn     event-msg-handler* [rabbit-data {:as ev-msg :keys [id ?data event]}]
  (event-msg-handler rabbit-data ev-msg))

(do ; Client-side methods
  (defmethod event-msg-handler :default ; Fallback
    [_ {:as ev-msg :keys [event]}]
    (println "Unhandled event: %s" event))

  (defmethod event-msg-handler :chsk/ws-ping
    [_ {:as ev-msg :keys [event]}])

  (defmethod event-msg-handler :chsk/uidport-open
    [_ {:as ev-msg :keys [uid]}]
    (println "new client connection"))

  (defmethod event-msg-handler :chat/message
    [rabbit-data {:as ev-msg :keys [?data uid id]}]
    (let [{:keys [msg channel] :as message} ?data]
      (println "Event from " uid ": " message)
      (db/insert-message uid msg channel)
      (let [relative-url (bidi/path-for router/routes :chat :channel (clojure.string/replace channel "#" ""))]
        (do-web-push (-> (assoc message :uid uid)
                         (assoc :url (str (:base-url config) relative-url))
                         (assoc :relative-url relative-url))))
      (publish rabbit-data (assoc message :uid uid) id)))

  (defmethod event-msg-handler :chat/typing
    [rabbit-data {:as ev-msg :keys [?data uid id]}]
    (let [message ?data]
      (publish rabbit-data (assoc message :uid uid) id)))

  (defmethod event-msg-handler :chat/history
    [_ {:as ev-msg :keys [?data uid send-fn]}]
    (let [channel (:channel ?data)
          messages (db/get-recent-messages channel)]
      (send-fn uid
               [:chat/history
                messages]))))

(defn sente-handler [{:keys [rabbit-mq]}]
  (let [{:keys [ch]} rabbit-mq]
    (partial event-msg-handler* {:ch ch :qname "hello"})))

(defn rabbit-message-handler
  [chsk-send! connected-uids
   ch {:keys [content-type delivery-tag type] :as meta} ^bytes byte-payload]
  (let [payload (deserialize byte-payload)]
    (println "Broadcasting server>user: %s" @connected-uids)
    (println "sending: " payload)
    (doseq [uid (:any @connected-uids)]
      (chsk-send! uid
                  [(keyword type)
                   payload]))))

(defrecord Messager [sente rabbit-mq]
  component/Lifecycle
  (start [component]
    (let [qname "hello"
          {:keys [chsk-send! connected-uids]} sente
          {:keys [ch]} rabbit-mq]
      (println (format "[main] Connected. Channel id: %d" (.getChannelNumber ch)))
      (lq/declare ch qname {:exclusive false :auto-delete false})
      (lc/subscribe ch qname (partial rabbit-message-handler chsk-send! connected-uids) {:auto-ack true})
      component))
  (stop [component]
    component))

(defn new-messager []
  (map->Messager {}))

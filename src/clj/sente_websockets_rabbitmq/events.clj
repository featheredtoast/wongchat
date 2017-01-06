(ns sente-websockets-rabbitmq.events
  (:require
   [langohr.queue     :as lq]
   [langohr.consumers :as lc]
   [langohr.basic     :as lb]
   [clojure.data.json :as json]
   [com.stuartsierra.component :as component]
   [sente-websockets-rabbitmq.db :as db]))

(defn publish [rabbit-data msg type]
  "publish an event to rabbit in json. rabbit-data is a map of {:ch :qname}"
  (lb/publish (:ch rabbit-data) "" (:qname rabbit-data) (json/write-str msg) {:content-type "text/json" :type type}))

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

  (defmethod event-msg-handler :chat/init
    [_ {:as ev-msg :keys [?data uid send-fn]}]
    (send-fn uid
             [:chat/init
              (db/get-recent-messages)]))

  (defmethod event-msg-handler :chat/submit
    [rabbit-data {:as ev-msg :keys [?data uid]}]
    (let [msg (:msg ?data)]
      (println "Event from " uid ": " msg)
      (db/insert-message uid msg)
      (publish rabbit-data {:msg msg :uid uid} "message")))
  
  (defmethod event-msg-handler :chat/typing
    [rabbit-data {:as ev-msg :keys [?data uid]}]
    (let [msg (:msg ?data)]
      (println "Event from " uid ": " msg)
      (publish rabbit-data {:msg msg :uid uid} "typing"))))

(defn sente-handler [{:keys [rabbit-mq]}]
  (let [{:keys [ch]} rabbit-mq]
    (partial event-msg-handler* {:ch ch :qname "hello"})))

(defn rabbit-message-handler
  [chsk-send! connected-uids
   ch {:keys [content-type delivery-tag type] :as meta} ^bytes payload]
  (let [{msg :msg sender-uid :uid :as payload}
        (json/read-str (String. payload "UTF-8")
                       :key-fn keyword)]
    (println "Broadcasting server>user: %s" @connected-uids)
    (println "sending: " payload)
    (doseq [uid (:any @connected-uids)]
      (chsk-send! uid
                  [(keyword "chat" type)
                   {:msg msg
                    :uid sender-uid}]))))

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


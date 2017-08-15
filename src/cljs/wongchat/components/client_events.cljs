(ns wongchat.components.client-events
  (:require [com.stuartsierra.component :as component]
            [wongchat.core :as core]))

(defrecord EventHandler [hammer]
  component/Lifecycle
  (start [component]
    (println "starting event handler component...")
    (core/start-focus-listener)
    (assoc component :hammer (core/setup-swipe-events (aget js/document "body"))))
  (stop [component]
    (println "stopping event handler component...")
    (core/stop-hammer hammer)
    (core/stop-focus-listener)
    (dissoc component :hammer)))
(defn new-event-handler []
  (map->EventHandler {}))

(defrecord OnlineHandler [sente online-handler]
  component/Lifecycle
  (start [component]
    (let [online-handler (core/start-online-handler sente)]
      (assoc component :online-handler online-handler)))
  (stop [component]
    (core/stop-online-handler online-handler)
    (dissoc component :online-handler)))
(defn new-online-handler []
  (map->OnlineHandler {}))

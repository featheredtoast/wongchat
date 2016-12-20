(ns sente-websockets-rabbitmq.system.rabbitmq
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]))

(defrecord Rabbit [uri conn ch]
  component/Lifecycle
  (start [component]
    (println "starting rabbit...")
    (let [conn (rmq/connect {:uri uri})
          ch   (lch/open conn)]
      (assoc component :conn conn :ch ch)))
  (stop [component]
    (when ch (rmq/close ch))
    (when conn (rmq/close conn))
    (assoc component
           :conn nil
           :ch nil)))

(defn new-rabbit-mq [uri]
  (map->Rabbit {:uri uri}))

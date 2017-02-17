(ns sente-websockets-rabbitmq.components
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]))

(defrecord Rabbit [uri conn ch]
  component/Lifecycle
  (start [component]
    (let [conn (rmq/connect {:uri uri})
          ch   (lch/open conn)]
      (assoc component :conn conn :ch ch)))
  (stop [component]
    (try (rmq/close ch)
         (catch com.rabbitmq.client.AlreadyClosedException e nil))
    (try (rmq/close conn)
         (catch com.rabbitmq.client.AlreadyClosedException e nil))
    component))

(defn new-rabbit-mq [uri]
  (map->Rabbit {:uri uri}))

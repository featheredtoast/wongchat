(ns wongchat.components.router
  (:require [com.stuartsierra.component :as component]
            [wongchat.core :as core]
            [pushy.core :as pushy]
            [bidi.bidi :as bidi]
            [wongchat.router :as router]))

(defrecord Router [history]
  component/Lifecycle
  (start [component]
    (let [match-route (partial bidi/match-route router/routes)
          history (pushy/pushy core/route! match-route)]
      (when-let [sw (aget js/navigator "serviceWorker")]
        (.addEventListener sw "message" (partial sw-on-message history)))
      (pushy/start! history)
      (assoc component :history history)))
  (stop [component]
    (pushy/stop! history)
    (dissoc component :history)))
(defn new-router []
  (map->Router {}))

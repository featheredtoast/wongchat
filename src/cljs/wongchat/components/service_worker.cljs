(ns wongchat.components.service-worker
  (:require [com.stuartsierra.component :as component]
            [wongchat.core :as core]))

(defrecord ServiceWorker []
  component/Lifecycle
  (start [component]
    (when-let [sw (aget js/navigator "serviceWorker")]
      (println "starting service worker...")
      (-> sw
          (.register "/sw.js")
          (.then core/sw-register)
          (.catch core/sw-error)))
    component)
  (stop [component]
    component))
(defn new-service-worker []
  (map->ServiceWorker {}))

(defrecord ClientPermissions []
  component/Lifecycle
  (start [component]
    (-> js/navigator.serviceWorker.ready
        (.then (fn [reg]
                 (.getSubscription (aget reg "pushManager"))))
        (.then (fn [subscription]
                 (swap! core/app-state assoc :subscribed? (some? subscription)))))
    component)
  (stop [component]
    component))
(defn new-client-permissions []
  (map->ClientPermissions {}))

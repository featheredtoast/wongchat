(ns wongchat.system
  (:require [com.stuartsierra.component :as component]
            [wongchat.core :as core]
            [system.components.sente :refer [new-channel-socket-client]]
            [wongchat.components.ui :refer [new-ui-component]]
            [wongchat.components.sente-handler :refer [new-sente-handler]]
            [wongchat.components.client-events :refer [new-event-handler
                                                       new-online-handler]]
            [wongchat.components.service-worker :refer [new-service-worker
                                                        new-client-permissions]]
            [wongchat.components.router :refer [new-router]]))

(declare system)

(defn new-system []
  (component/system-map
   :app-root (new-ui-component)
   :sente (new-channel-socket-client)
   :sente-handler (component/using
                   (new-sente-handler)
                   [:sente])
   :event-handler (new-event-handler)
   :online-event-handler (component/using
                          (new-online-handler)
                          [:sente])
   :service-worker (new-service-worker)
   :client-permissions (new-client-permissions)
   :router (new-router)))

(defn init []
  (set! system (new-system)))

(defn start []
  (set! system (component/start system)))

(defn stop []
  (set! system (component/stop system)))

(defn ^:export go []
  (init)
  (start))

(defn reset []
  (stop)
  (go))

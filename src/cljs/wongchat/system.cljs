(ns wongchat.system
  (:require [com.stuartsierra.component :as component]
            [wongchat.app :refer [chat-system]]))

(declare system)

(defn new-system []
  (chat-system))

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

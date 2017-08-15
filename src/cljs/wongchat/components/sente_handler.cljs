(ns wongchat.components.sente-handler
  (:require [com.stuartsierra.component :as component]
            [wongchat.core :as core]
            [taoensso.sente  :as sente]
            [cljs.core.async :as async :refer [put!]]))

(defrecord SenteHandler [router chsk sente]
  component/Lifecycle
  (start [component]
    (let [{:keys [chsk-send! chsk ch-chsk]} sente]
      (core/start-message-sender chsk-send!)
      (assoc component
             :chsk chsk
             :router
             (sente/start-chsk-router! ch-chsk (partial core/event-msg-handler* chsk-send!)))))
  (stop [component]
    (println "sending shutdown message!")
    (put! core/message-chan {:type :shutdown})
    (when chsk
      (println "disconnecting...")
      (sente/chsk-disconnect! chsk))
    (when-let [stop-f router]
      (println "stopping router...")
      (stop-f))
    (assoc component
           :chsk nil
           :router nil)))
(defn new-sente-handler []
  (map->SenteHandler {}))

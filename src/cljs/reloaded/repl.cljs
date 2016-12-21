(ns ^:figwheel-no-load reloaded.repl
  (:require [com.stuartsierra.component :as component]))

(def system (atom))

(def initializer (atom))

(defn set-init! [init]
  (swap! initializer (constantly init)))

(defn- stop-system [s]
  (if s (component/stop s)))

(defn- init-error []
  (js/Error. "No system initializer function found."))

(defn init []
  (if-let [init @initializer]
    (do (swap! system #(do (stop-system %) (init))) :ok)
    (throw (init-error))))

(defn start []
  (swap! system component/start)
  :started)

(defn stop []
  (swap! system stop-system)
  :stopped)

(defn go []
  (init)
  (start))

(defn clear []
  (swap! system #(do (stop-system %) nil))
  :ok)

(defn before-reload []
  (println "before reload... stopping!")
  (stop))

(defn after-reload []
  (println "after reload... going!")
  (go))

(defn set-init-go! [init]
  (set-init! init)
  (when-not @system
    (do
      (println "starting..")
      (go))))

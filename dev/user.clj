(ns user
  (:require [wongchat.application]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.config :as fw-config]
            [figwheel-sidecar.system :as fw-sys]
            [reloaded.repl :refer [system init]]
            [ring.middleware.file :refer [wrap-file]]
            [system.components.middleware :refer [new-middleware]]
            [figwheel-sidecar.repl-api :as figwheel]
            [garden-watcher.core :refer [new-garden-watcher]]
            [wongchat.config :refer [config]]
            [repl-watcher.core :refer [repl-watcher]]
            [wongchat.db :refer [migrate rollback]]))

(defn get-dev-middleware [redis-url]
  (-> (wongchat.application/get-middleware redis-url)
      (conj [wrap-file "dev-target" {:allow-symlinks? true}])))

(defn dev-system []
  (let [config (config)]
    (assoc (wongchat.application/app-system config)
           :middleware (new-middleware
                        {:middleware (get-dev-middleware (:redis-url config))})
           :figwheel-system (fw-sys/figwheel-system (fw-config/fetch-config))
           :css-watcher (fw-sys/css-watcher {:watch-paths ["resources/public/css"]})
           :garden-watcher (new-garden-watcher ['wongchat.styles])
           :auto-reload (repl-watcher ["src/clj"] "(reloaded.repl/reset)"))))

(reloaded.repl/set-init! #(dev-system))

(defn cljs-repl []
  (fw-sys/cljs-repl (:figwheel-system system)))

;; Set up aliases so they don't accidentally
;; get scrubbed from the namespace declaration
(def start reloaded.repl/start)
(def stop reloaded.repl/stop)
(def go reloaded.repl/go)
(def reset reloaded.repl/reset)
(def reset-all reloaded.repl/reset-all)

;; deprecated, to be removed in Chestnut 1.0
(defn run []
  (println "(run) is deprecated, use (go)")
  (go))

(defn browser-repl []
  (println "(browser-repl) is deprecated, use (cljs-repl)")
  (cljs-repl))

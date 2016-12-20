(ns user
  (:require [sente-websockets-rabbitmq.server]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.config :as config]
            [figwheel-sidecar.system :as sys]
            [figwheel-sidecar.repl-api :as figwheel]
            [reloaded.repl :refer [system start stop go reset reset-all]]
            [system.components.watcher :as watcher]))

;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn dev-system []
  (sente-websockets-rabbitmq.server/prod-system)
  #_(merge
   (sente-websockets-rabbitmq.server/prod-system)
   (component/system-map
    :figwheel-system (sys/figwheel-system (config/fetch-config))
    :css-watcher (sys/css-watcher {:watch-paths ["resources/public/css"]}))))

(defn init []
  (reloaded.repl/set-init! dev-system))

(defn browser-repl []
  (sys/cljs-repl (:figwheel-system system)))

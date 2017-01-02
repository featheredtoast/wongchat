(ns user
  (:require [sente-websockets-rabbitmq.application]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.config :as fw-config]
            [figwheel-sidecar.system :as fw-sys]
            [figwheel-sidecar.repl-api :as figwheel]
            [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
            [reloaded.repl :refer [system init start stop go reset reset-all]]
            [sente-websockets-rabbitmq.config :refer [config]]))

;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn dev-system [config]
  (merge
   (sente-websockets-rabbitmq.application/app-system config)
   (component/system-map
    :figwheel-system (fw-sys/figwheel-system (fw-config/fetch-config))
    :css-watcher (fw-sys/css-watcher {:watch-paths ["resources/public/css"]}))))

(set-refresh-dirs "src" "dev")
(reloaded.repl/set-init! #(dev-system config))

(defn reload []
  (reset))

(defn run []
  (go))

(defn browser-repl []
  (fw-sys/cljs-repl (:figwheel-system system)))

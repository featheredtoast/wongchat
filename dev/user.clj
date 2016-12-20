(ns user
  (:require [sente-websockets-rabbitmq.server]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.config :as config]
            [figwheel-sidecar.system :as sys]
            [figwheel-sidecar.repl-api :as figwheel]
            [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
            [reloaded.repl :refer [system start stop go reset reset-all]]
            [system.components.watcher :as watcher]
            [sente-websockets-rabbitmq.server :refer [prod-system]]))

;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(set-refresh-dirs "src" "dev")

(reloaded.repl/set-init! #(prod-system))

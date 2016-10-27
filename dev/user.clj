(ns user
  (:require [sente-websockets-rabbitmq.server]
            [com.stuartsierra.component :as component]
            [figwheel-sidecar.config :as config]
            [figwheel-sidecar.system :as sys]
            [ring.middleware.reload :refer [wrap-reload]]
            [figwheel-sidecar.repl-api :as figwheel]
            [reloaded.repl :refer [system init start stop go reset reset-all]]
            (system.components
             [http-kit :refer [new-web-server]]
             [sente :refer [new-channel-sockets sente-routes]]
             [endpoint :refer [new-endpoint]]
             [handler :refer [new-handler]]
             [middleware :refer [new-middleware]])
            [system.components.watcher :as watcher]))

;; Let Clojure warn you when it needs to reflect on types, or when it does math
;; on unboxed numbers. In both cases you should add type annotations to prevent
;; degraded performance.
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(def http-handler
  (wrap-reload #'sente-websockets-rabbitmq.server/http-handler))

(defn get-http-handler [config]
  http-handler)

(defn start-workers []
  (#'sente-websockets-rabbitmq.server/start-workers!))

(defn dev-system []
  (merge
   (sente-websockets-rabbitmq.server/prod-system)
   (component/system-map
    :figwheel-system (sys/figwheel-system (config/fetch-config))
    :css-watcher (sys/css-watcher {:watch-paths ["resources/public/css"]}))))

(defn reload []
  (reset))

(defn run []
  (reloaded.repl/set-init! dev-system)
  (go))

(defn browser-repl []
  (sys/cljs-repl (:figwheel-system system)))

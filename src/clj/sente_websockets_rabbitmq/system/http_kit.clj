(ns sente-websockets-rabbitmq.system.http-kit
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [system.schema :as sc]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! take! chan go go-loop timeout alts!)]))

(defrecord WebServer [options server handler boops]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server (run-server handler options)
          c (chan)]
      (println "starting boop...")
      (go-loop []
        (let [[v ch] (alts! [c (timeout 3000)])]
          (if (= :stop v)
            (println "stopping boops")
            (do
              (println "boop!")
              (recur)))))
      (assoc component
             :server server
             :boops (fn [] (put! c :stop)))))
  (stop [component]
    (when server (server))
    (when boops (boops))
    (assoc component
           :server nil)))

(def Options
  {(s/optional-key :ip) sc/IpAddress
   (s/optional-key :port) sc/Port
   (s/optional-key :thread) sc/PosInt
   (s/optional-key :worker-name-prefix) s/Str
   (s/optional-key :queue-size) sc/PosInt
   (s/optional-key :max-body) sc/PosInt
   (s/optional-key :max-line) sc/PosInt})

(defn new-web-server
  ([port]
   (new-web-server port nil {}))
  ([port handler]
   (new-web-server port handler {}))
  ([port handler options]
   (map->WebServer {:options (s/validate Options 
                                         (merge {:port port}
                                                options))
                    :handler handler})))

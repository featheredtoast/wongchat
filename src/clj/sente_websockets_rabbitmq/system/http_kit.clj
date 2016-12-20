(ns sente-websockets-rabbitmq.system.http-kit
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [system.schema :as sc]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! take! chan go go-loop timeout alts!)]))

(defrecord WebServer [options server handler stop-boops]
  component/Lifecycle
  (start [component]
    (let [handler (get-in component [:handler :handler] handler)
          server (run-server handler options)
          boop-chan (chan)
          stop-boops (fn [] (do
                              (println "stopping boops")
                              (put! boop-chan :stop)))
          go-boops (fn [] (go-loop []
                            (println "boops are happening..")
                            (let [[v ch] (alts! boop-chan (timeout 1000))]
                              (println "yes they are!")
                              (if (= v :stop)
                                (println "stopping")
                                (do
                                  (println "BOOP!")
                                  (recur))))))]
      (println "starting boop...")
      (go-boops)
      (assoc component
             :server server
             :stop-boops stop-boops)))
  (stop [component]
    (when server (server))
    (when stop-boops (stop-boops))
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

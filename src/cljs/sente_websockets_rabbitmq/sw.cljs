(ns sente-websockets-rabbitmq.sw)

(enable-console-print!)

(def *cache-name* "cache-v1")
(def urls-to-cache (clj->js ["/chat"]))

(defn install-sw [event]
  (.waitUntil event
              (-> (.open js/caches *cache-name*)
                  (.then (fn [cache]
                           (println "opened cache")
                           (-> (js/fetch "/chat" #js{:credentials "include"})
                               (.then (fn [resp]
                                        (.put cache "/chat" resp)))))))))
(.addEventListener js/self "install" install-sw)
(defn fetch-listen [event]
  (.respondWith event
                (-> (.match js/caches (.request event))
                    (.then (fn [response]
                             (println "response: " response)
                             (if response
                               (do
                                 (println "response from cache")
                                 response)
                               (do
                                 (println "fetch from server...")
                                 (js/fetch (.request event)))))))))
(.addEventListener js/self "fetch" fetch-listen)

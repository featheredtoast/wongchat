(ns sente-websockets-rabbitmq.sw
  (:require [sente-websockets-rabbitmq.data :refer [serialize deserialize]]))

(enable-console-print!)

(def *cache-name* "cache-v1")
(def urls-to-cache ["/chat"
                    "/css/style.css"
                    "/js/compiled/sente_websockets_rabbitmq.js"])

(defn add-cache [cache url]
  (println "adding cache: " url)
  (-> (js/fetch url #js{:credentials "include"})
      (.then (fn [resp]
               (.put cache url resp)))))

(defn add-cache-from-response [cache-name request response]
  (println "adding cache: " js/request.url)
  (-> (.open js/caches cache-name)
      (.then (fn [cache]
               (.put cache request response)))))

(defn add-all-cache [cache urls]
  (println "adding all..." urls)
  (dorun (map (partial add-cache cache) urls)))

(defn install-sw [event]
  (.waitUntil event
              (println "install sw")
              (-> (.open js/caches *cache-name*)
                  (.then (fn [cache]
                           (println "opened cache")
                           (add-all-cache cache urls-to-cache))))))
(.addEventListener js/self "install" install-sw)

(defn fetch-and-cache [request]
  (let [fetch-request (.clone request)]
    (-> (js/fetch fetch-request)
        (.then
         (fn [response]
           (if (or (not response)
                   (<= 400 js/response.status 599))
             (do
               (println "not caching. this is a butt response for " js/request.url " | " js/response.status " | " js/response.type)
               response)
             (do
               (println "caching and responding...!")
               (let [response-to-cache (.clone response)]
                 (add-cache-from-response *cache-name* request response-to-cache)
                 response))))))))

(defn fetch-listen [event]
  (let [request (aget event "request")]
    (.respondWith event
                  (-> (.match js/caches request)
                      (.then (fn [response]
                               (if (and response false)
                                 (do
                                   (println "response from cache for: " (aget request "url"))
                                   response)
                                 (do
                                   (println "fetch from server for: " (aget request "url"))
                                   (fetch-and-cache js/event.request)))))))))
#_(.addEventListener js/self "fetch" fetch-listen)

(defn on-push [event]
  (let [{:keys [msg channel uid] :as event} (deserialize (.text js/event.data))]
    (println "push received" msg)
    (.waitUntil event (.showNotification js/self.registration (str channel " "  uid " says...") #js{:body (str msg)}))))
(.addEventListener js/self "push" on-push)

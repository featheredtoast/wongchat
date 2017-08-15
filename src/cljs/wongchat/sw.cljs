(ns wongchat.sw
  (:require [wongchat.data :refer [deserialize]]))

(enable-console-print!)

(def *cache-name* "cache-v1")
(def urls-to-cache ["/css/style.css"
                    "/js/compiled/wongchat.js"])

(defn add-cache [cache url]
  (println "adding cache: " url)
  (-> (js/fetch url #js{:credentials "include"})
      (.then (fn [resp]
               (.put cache url resp)))))

(defn add-cache-from-response [cache-name request response]
  (println "adding cache: " (aget request "url"))
  (-> (.open js/caches cache-name)
      (.then (fn [cache]
               (.put cache request response)))))

(defn add-all-cache [cache urls]
  (println "adding all..." urls)
  (dorun (map (partial add-cache cache) urls)))

(defn install-sw [event]
  (.waitUntil event
              (-> (.open js/caches *cache-name*)
                  (.then (fn [cache]
                           (println "opened cache")
                           (add-all-cache cache urls-to-cache))))))
(.addEventListener js/self "install" install-sw)

(defn activate-sw [event]
  (.waitUntil event (js/self.clients.claim)))
(.addEventListener js/self "activate" activate-sw)

(defn fetch-and-cache [request]
  (let [fetch-request (.clone request)]
    (-> (js/fetch fetch-request)
        (.then
         (fn [response]
           (if (or (not response)
                   (<= 400 (aget response "status") 599))
             (do
               (println "not caching. this is a butt response for " (aget request "url") " | " (aget response "status") " | " (aget response "type"))
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
                                   (fetch-and-cache (aget event "request"))))))))))
#_(.addEventListener js/self "fetch" fetch-listen)

(defn pop-notification [channel uid msg url relative-url]
  (.showNotification
   js/self.registration
   (str "New message in " channel)
   #js{:body (str uid ": " msg)
       :data #js{:url url
                 :relativeUrl relative-url}
       :icon "/images/speech-bubble-xxl.png"}))

(defn on-push [event]
  (let [{:keys [msg channel uid url relative-url]} (deserialize (.text (aget event "data")))]
    (println "push received" msg)
    (.waitUntil
     event
     (-> (.getNotifications js/self.registration)
         (.then
          (fn [notifications]
            (when notifications
              (dorun (for [notification notifications]
                       (.close notification))))
            (pop-notification channel uid msg url relative-url)))))))
(.addEventListener js/self "push" on-push)

(defn notification-click [event]
  (.close (aget event "notification"))
  (let [url (aget event "notification" "data" "url")
        relative-url (aget event "notification" "data" "relativeUrl")]
     (.waitUntil
      event
      (-> (.matchAll js/clients #js{:type "window"})
          (.then
           (fn [client-list]
             (if (and client-list (< 0 (count client-list)))
               (let [client (first client-list)]
                 (.focus client)
                 (println "post message with url: " relative-url)
                 (.postMessage client #js{:url relative-url}))
               (.openWindow js/clients url))))))))
(.addEventListener js/self "notificationclick" notification-click)

(ns wongchat.sw
  (:require [wongchat.data :refer [serialize deserialize]]))

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
  (println "adding cache: " js/request.url)
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

(defn pop-notification [channel uid msg url]
  (.showNotification
   js/self.registration
   (str "New message in " channel)
   #js{:body (str uid ": " msg)
       :data #js{:url url}
       :icon "/images/speech-bubble-xxl.png"}))

(defn on-push [event]
  (let [{:keys [msg channel uid url]} (deserialize (.text js/event.data))]
    (println "push received" msg)
    (.waitUntil
     event
     (-> (.getNotifications js/self.registration)
         (.then
          (fn [notifications]
            (when notifications
              (dorun (for [notification notifications]
                       (.close notification))))
            (pop-notification channel uid msg url)))))))
(.addEventListener js/self "push" on-push)

(defn notification-click [event]
   (.close js/event.notification)
   (let [url js/event.notification.data.url]
     (.waitUntil
      event
      (-> (.matchAll js/clients #js{:type "window"})
          (.then
           (fn [client-list]
             (if (and client-list (< 0 (count client-list)))
               (dorun
                (take-while
                 false?
                 (for [client client-list]
                   (do (.focus client)
                       true))))
               (.openWindow js/clients url))))))))
(.addEventListener js/self "notificationclick" notification-click)

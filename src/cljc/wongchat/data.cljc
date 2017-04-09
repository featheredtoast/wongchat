(ns wongchat.data
  (:require [cognitect.transit :as transit]))

#?(:cljs
   (do
     (defn deserialize [json]
       (transit/read (transit/reader :json) json))

     (defn serialize [data]
       (transit/write (transit/writer :json) data)))
   :clj
   (do
     (defn deserialize [payload]
       (let [byte-payload (if (= java.lang.String (type payload)) (.getBytes payload) payload)
             in (java.io.ByteArrayInputStream. byte-payload)
             reader (transit/reader in :json)]
         (transit/read reader)))

     (defn serialize [data]
       (let [out (java.io.ByteArrayOutputStream. 4096)
             writer (transit/writer out :json)]
         (transit/write writer data)
         (.toString out)))))

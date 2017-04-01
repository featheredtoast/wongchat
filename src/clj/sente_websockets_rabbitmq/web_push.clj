(ns sente-websockets-rabbitmq.web-push
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json])
  (:import [org.bouncycastle.jce ECNamedCurveTable]
           [java.security KeyPairGenerator SecureRandom Security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [org.bouncycastle.asn1.pkcs PrivateKeyInfo]
           [org.bouncycastle.asn1.x509 SubjectPublicKeyInfo]
           [org.bouncycastle.jce.spec ECPrivateKeySpec]
           [java.io StringWriter]
           [java.util Base64]
           [org.apache.commons.codec.binary Hex]
           [org.apache.commons.io IOUtils]
           [org.jose4j.jws JsonWebSignature AlgorithmIdentifiers]
           [org.jose4j.jwt JwtClaims]
           [java.net URL]
           [nl.martijndwars.webpush Utils PushService Notification]))

;; this was by far the most informative article on what the keys were supposed to look like:
;; https://blog.mozilla.org/services/2016/08/23/sending-vapid-identified-webpush-notifications-via-mozillas-push-service/

(defn encrypt [payload user-key user-auth]
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [padsize 2
        b64-encoder (-> (Base64/getUrlEncoder)
                        (.withoutPadding))
        obj (PushService/encrypt (.getBytes payload) (Utils/loadPublicKey user-key) (.getBytes user-auth) padsize)
        pubkey (.encodeToString b64-encoder (Utils/savePublicKey (.getPublicKey obj)))
        salt (.encodeToString b64-encoder (.getSalt obj))
        cipher-text (.getCiphertext obj)]
    {:encrypt-pubkey pubkey :salt salt :cipher-text cipher-text}))

;;an attempt with https://github.com/web-push-libs/web-push-java/blob/3d9f1503aff27a0b96f911a43c247bab5c9660c5/src/main/java/com/ameyakarve/browserpushjava/EllipticCurveKeyUtil.java ?
(defn get-ecdh-encoded-public-key [pkey]
  (let [point (.getW pkey)
        x (.toString (.getAffineX point) 16)
        y (.toString (.getAffineY point) 16)
        sb (doto (java.lang.StringBuilder.)
             (.append "04")
             (.append (apply str (repeat (- 64 (count x)) 0)))
             (.append x)
             (.append (apply str (repeat (- 64 (count y)) 0)))
             (.append y))
        b64-encoder (-> (Base64/getUrlEncoder)
                        (.withoutPadding))]
    (.encodeToString b64-encoder (Hex/decodeHex (.toCharArray (.toString sb))))))

(defn get-ecdh-encoded-private-key [key]
  (let [s (.toString (.getS key) 16)
        sb (doto (java.lang.StringBuilder.)
             (.append (apply str (repeat (- 64 (count s)) 0)))
             (.append s))
        b64-encoder (-> (Base64/getUrlEncoder)
                        (.withoutPadding))]
    (.encodeToString b64-encoder (Hex/decodeHex (.toCharArray (.toString sb))))))

;; https://bouncycastle.org/wiki/display/JA1/Elliptic+Curve+Key+Pair+Generation+and+Key+Factories
(defn gen-ecdh-key []
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [ecSpec (ECNamedCurveTable/getParameterSpec "secp256r1")
        g (doto (KeyPairGenerator/getInstance "ECDH" "BC")
            (.initialize ecSpec (SecureRandom.)))
        generated-key-pair (.generateKeyPair g)
        b64-encoder (Base64/getEncoder)
        out-priv (java.io.ByteArrayOutputStream.)
        public-key (get-ecdh-encoded-public-key (.getPublic generated-key-pair))
        private-key (get-ecdh-encoded-private-key (.getPrivate generated-key-pair))]
    {:public public-key :private private-key}))

(defn tomorrow []
  (let [dt (java.util.Date.)
        d (.getTime (doto (java.util.Calendar/getInstance)
                      (.setTime dt)
                      (.add java.util.Calendar/DATE 1)))]
    d))

(defn get-audience [endpoint]
  (let [url (URL. endpoint)]
    (str (.getProtocol url) "://" (.getHost url))))

;; https://github.com/auth0/java-jwt
;; I'd really like to see a move to https://github.com/liquidz/clj-jwt but this works for now.
(defn gen-jwt-key [creds email endpoint]
  (let [audience (get-audience endpoint)
        privKey (Utils/loadPrivateKey creds)
        claims (doto (JwtClaims.)
                 (.setExpirationTimeMinutesInTheFuture (* 12 60))
                 (.setSubject (str "mailto:" email))
                 (.setAudience audience))
        jws (doto (JsonWebSignature.)
              (.setHeader "typ" "JWT")
              (.setHeader "alg" "ES256")
              (.setPayload (.toJson claims))
              (.setKey privKey)
              (.setAlgorithmHeaderValue (AlgorithmIdentifiers/ECDSA_USING_P256_CURVE_AND_SHA256)))]
    (.getCompactSerialization jws)))

(defn get-headers [keys email endpoint salt encrypt-pubkey]
  {"Authorization" (str "WebPush " (gen-jwt-key (:private keys) email endpoint))
   "Crypto-Key" (str "keyid=p256dh;dh=" encrypt-pubkey ";" "p256ecdsa=" (:public keys))
   "Encryption" (str "keyid=p256dh;salt=" salt)
   "TTL" "60"
   "Content-Encoding" "aesgcm"})

(defn do-push! [keys client-data-json email payload]
  (let [client-data (json/read-str client-data-json)
        endpoint (get client-data "endpoint")
        client-key (get-in client-data ["keys" "p256dh"])
        client-auth (get-in client-data ["keys" "auth"])
        {:keys [encrypt-pubkey salt cipher-text]} (encrypt payload client-key client-auth)
        headers (get-headers keys email endpoint salt encrypt-pubkey)]
    (println "endpoint: " endpoint " headers: " headers " body: " cipher-text)
    (println (http/post
              endpoint
              {:headers headers
               :body cipher-text}))))

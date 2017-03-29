(ns sente-websockets-rabbitmq.web-push
  (:import [org.bouncycastle.jce ECNamedCurveTable]
           [java.security KeyPairGenerator SecureRandom Security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [org.bouncycastle.asn1.pkcs PrivateKeyInfo]
           [org.bouncycastle.asn1.x509 SubjectPublicKeyInfo]
           [java.io StringWriter]
           [java.util Base64]
           [org.apache.commons.codec.binary Hex]))

;; this was by far the most informative article on what the keys were supposed to look like:
;; https://blog.mozilla.org/services/2016/08/23/sending-vapid-identified-webpush-notifications-via-mozillas-push-service/

;; https://bouncycastle.org/wiki/display/JA1/Elliptic+Curve+Key+Pair+Generation+and+Key+Factories
(defn gen-ecdh-key []
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [ecSpec (ECNamedCurveTable/getParameterSpec "prime256v1")
        g (doto (KeyPairGenerator/getInstance "ECDSA" "BC")
            (.initialize ecSpec (SecureRandom.)))
        generated-key-pair (.generateKeyPair g)
        b64-encoder (Base64/getEncoder)
        out-priv (java.io.ByteArrayOutputStream.)
        public-key (.encodeToString b64-encoder (.getEncoded (.getPublic generated-key-pair)))
        private-key (.encodeToString b64-encoder (.getEncoded (.getPrivate generated-key-pair)))]
    {:public public-key :private private-key}))

;; http://stackoverflow.com/questions/4600106/create-privatekey-from-byte-array
(defn decode-key [key]
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [kf (KeyFactory/getInstance "ECDSA" "BC")
        b64-decoder (Base64/getDecoder)
        bytes (.decode b64-decoder key)
        ks (PKCS8EncodedKeySpec. bytes)
        pk (.generatePrivate kf ks)]
    pk))

(defn decode-public-key [key]
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [kf (KeyFactory/getInstance "ECDSA" "BC")
        b64-decoder (Base64/getDecoder)
        bytes (.decode b64-decoder key)
        ks (X509EncodedKeySpec. bytes)
        pk (.generatePublic kf ks)]
    pk))

;;an attempt with https://github.com/web-push-libs/web-push-java/blob/3d9f1503aff27a0b96f911a43c247bab5c9660c5/src/main/java/com/ameyakarve/browserpushjava/EllipticCurveKeyUtil.java ?
(defn get-ecdh-encoded-public-key [public]
  (let [pkey (decode-public-key public)
        point (.getW pkey)
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

(defn tomorrow []
  (let [dt (java.util.Date.)
        d (.getTime (doto (java.util.Calendar/getInstance)
                      (.setTime dt)
                      (.add java.util.Calendar/DATE 1)))]
    d))

;; https://github.com/auth0/java-jwt
;; I'd really like to see a move to https://github.com/liquidz/clj-jwt but this works for now.
(defn gen-jwt-key [creds email]
  (let [privKey (decode-key creds)
        algorithm (com.auth0.jwt.algorithms.Algorithm/ECDSA256 privKey)]
    (-> (com.auth0.jwt.JWT/create)
        (.withHeader {"typ" "JWT"})
        (.withSubject (str "mailto:" email))
        (.withExpiresAt (tomorrow))
        (.sign algorithm))))

(defn get-headers [keys email]
  {:Authorization (str "Webpush " (gen-jwt-key (:private keys) email))
   :Crypto-Key (str "p256ecdsa=" (get-ecdh-encoded-public-key (:public keys)))})

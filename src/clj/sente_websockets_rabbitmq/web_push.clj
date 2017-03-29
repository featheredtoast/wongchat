(ns sente-websockets-rabbitmq.web-push
  (:import [org.bouncycastle.jce ECNamedCurveTable]
           [java.security KeyPairGenerator SecureRandom Security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [org.bouncycastle.asn1.pkcs PrivateKeyInfo]
           [org.bouncycastle.asn1.x509 SubjectPublicKeyInfo]
           [java.io StringWriter]
           [java.util Base64]))

;; https://bouncycastle.org/wiki/display/JA1/Elliptic+Curve+Key+Pair+Generation+and+Key+Factories
(defn gen-ecdh-key []
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [ecSpec (ECNamedCurveTable/getParameterSpec "prime256v1")
        g (doto (KeyPairGenerator/getInstance "ECDH" "BC")
            (.initialize ecSpec (SecureRandom.)))
        generated-key-pair (.generateKeyPair g)
        b64-encoder (-> (Base64/getUrlEncoder)
                        (.withoutPadding))
        out-priv (java.io.ByteArrayOutputStream.)
        public-key (.encodeToString b64-encoder (.getEncoded (.getPublic generated-key-pair)))
        private-key (.encodeToString b64-encoder (.getEncoded (.getPrivate generated-key-pair)))]
    {:public public-key :private private-key}))

(decode-key (:private (gen-ecdh-key)))

;; http://stackoverflow.com/questions/4600106/create-privatekey-from-byte-array
(defn decode-key [key]
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [kf (KeyFactory/getInstance "ECDH" "BC")
        b64-decoder (Base64/getUrlDecoder)
        bytes (.decode b64-decoder key)
        ks (PKCS8EncodedKeySpec. bytes)
        pk (.generatePrivate kf ks)]
    pk))

(defn tomorrow []
  (let [dt (java.util.Date.)
        d (.getTime (doto (java.util.Calendar/getInstance)
                      (.setTime dt)
                      (.add java.util.Calendar/DATE 1)))]
    d))

;; https://github.com/auth0/java-jwt
;; I'd really like to see a move to https://github.com/liquidz/clj-jwt but this works for now.
(defn gen-jwt-key [creds]
  (let [privKey (decode-key creds)
        algorithm (com.auth0.jwt.algorithms.Algorithm/ECDSA256 privKey)]
    (-> (com.auth0.jwt.JWT/create)
        (.withHeader {"typ" "JWT"})
        (.withSubject "mailto:admin@example.com")
        (.withExpiresAt (tomorrow))
        (.sign algorithm))))

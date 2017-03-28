(ns sente-websockets-rabbitmq.web-push
  (:import [org.bouncycastle.jce ECNamedCurveTable]
           [java.security KeyPairGenerator SecureRandom Security KeyFactory]
           [java.security.spec PKCS8EncodedKeySpec X509EncodedKeySpec]
           [org.bouncycastle.asn1.pkcs PrivateKeyInfo]
           [org.bouncycastle.asn1.x509 SubjectPublicKeyInfo]
           [org.bouncycastle.util.encoders Base64]
           [java.io StringWriter]))

;; https://bouncycastle.org/wiki/display/JA1/Elliptic+Curve+Key+Pair+Generation+and+Key+Factories
;; https://people.eecs.berkeley.edu/~jonah/bc/org/bouncycastle/util/encoders/Base64.html
(defn gen-ecdh-key []
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [ecSpec (ECNamedCurveTable/getParameterSpec "prime256v1")
        g (doto (KeyPairGenerator/getInstance "ECDH" "BC")
            (.initialize ecSpec (SecureRandom.)))
        generated-key-pair (.generateKeyPair g)
        out-pub (java.io.ByteArrayOutputStream.)
        out-priv (java.io.ByteArrayOutputStream.)
        public-key (do (Base64/encode (.getEncoded (.getPublic generated-key-pair)) out-pub)
                       (.toString out-pub))
        private-key (do (Base64/encode (.getEncoded (.getPrivate generated-key-pair)) out-priv)
                        (.toString out-priv))]
    {:public public-key :private private-key}))

;; http://stackoverflow.com/questions/4600106/create-privatekey-from-byte-array
(defn decode-key [key]
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [kf (KeyFactory/getInstance "ECDH" "BC")
        bytes (Base64/decode key)
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

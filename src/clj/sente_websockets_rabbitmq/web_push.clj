(ns sente-websockets-rabbitmq.web-push
  (:import [org.bouncycastle.jce ECNamedCurveTable]
           [java.security KeyPairGenerator SecureRandom Security]
           [org.bouncycastle.asn1.pkcs PrivateKeyInfo]
           [org.bouncycastle.asn1.x509 SubjectPublicKeyInfo]
           [java.util Base64]))

;;Org.BouncyCastle.Asn1.Pkcs.PrivateKeyInfo, Org.BouncyCastle.Pkcs.PrivateKeyInfoFactory, Org.BouncyCastle.Asn1.X509.SubjectPublicKeyInfo, Org.BouncyCastle.X509.SubjectPublicKeyInfoFactory

;;Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
;;ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("prime192v1");
;;KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");
;;g.initialize(ecSpec, new SecureRandom());
;;KeyPair pair = g.generateKeyPair();
(defn gen-ecdh-key []
  (Security/addProvider (org.bouncycastle.jce.provider.BouncyCastleProvider.))
  (let [b64-encoder (Base64/getEncoder)
        ecSpec (ECNamedCurveTable/getParameterSpec "prime256v1")
        g (KeyPairGenerator/getInstance "ECDSA" "BC")
        key-pair (doto g
                   (.initialize ecSpec (SecureRandom.)))
        generated-key-pair (.generateKeyPair g)
        public-key (.encodeToString b64-encoder (.getEncoded (.getPublic generated-key-pair)))
        public-encoding (.getFormat (.getPublic generated-key-pair))
        private-key (.encodeToString b64-encoder (.getEncoded (.getPrivate generated-key-pair)))
        encoding (.getFormat (.getPrivate generated-key-pair))]
    {:public public-key :private private-key
     :public-encoding public-encoding
     :encoding encoding}))

(gen-ecdh-key)

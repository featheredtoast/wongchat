(ns sente-websockets-rabbitmq.db
  (:require
   [com.stuartsierra.component :as component]
   [sente-websockets-rabbitmq.config :refer [config]]
   [sente-websockets-rabbitmq.web-push :refer [gen-ecdh-key get-headers]]
   [ragtime.jdbc]
   [ragtime.repl]
   [clojure.java.jdbc :as jdbc]
   [clj-time.core :as time]
   [clj-time.coerce :as timec]))

(def db-config
  {:classname "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname (:db-host config)
   :user (:db-user config)
   :password (:db-pass config)})

(defn get-recent-messages
  ([]
   (vec
    (reverse
     (jdbc/query db-config
                 ["select uid, msg from messages order by id DESC LIMIT 10;"]))))
  ([channel]
   (vec
    (reverse
     (jdbc/query db-config
                 ["select uid, msg from messages where channel = ? order by id DESC LIMIT 10;"
                  channel])))))

(defn get-user-messages [uid]
  (reverse
   (map :msg
        (jdbc/query db-config
                    ["select uid, msg from messages where uid = ? order by id DESC LIMIT 10;"
                     uid]))))

(defn insert-message
  ([uid msg]
   (insert-message uid msg "#general"))
  ([uid msg channel]
   (jdbc/insert! db-config :messages
                 {:uid uid :msg msg :channel channel :date (timec/to-timestamp (time/now))})))

(defn create-push-auth [uid subscription]
  (jdbc/insert! db-config :subscriptions
                {:uid uid :subscription subscription}))

(defn get-push-auth []
  (jdbc/query db-config
              ["select id, uid, subscription from subscriptions LIMIT 1000;"]))

(defn store-server-credentials [public private]
  (jdbc/insert! db-config :credentials
                {:public_key public :private_key private}))

(defn get-server-credentials []
  (if-let [credentials (first (jdbc/query db-config
                                          ["select public_key as public, private_key as private from credentials LIMIT 1;"]))]
    credentials
    (let [credentials (gen-ecdh-key)]
      (store-server-credentials (:public credentials) (:private credentials))
      credentials)))

(defn get-public-server-credentials []
  (:public (get-server-credentials)))

(defn get-private-server-credentials []
  (:private (get-server-credentials)))

(defn up []
  (ragtime.repl/migrate {:datastore
                           (ragtime.jdbc/sql-database db-config)
                         :migrations (ragtime.jdbc/load-resources "migrations")}))

(defn down []
  (ragtime.repl/rollback {:datastore
                          (ragtime.jdbc/sql-database db-config)
                          :migrations (ragtime.jdbc/load-resources "migrations")}))

(defrecord Migrate []
  component/Lifecycle
  (start [component]
    (println "migrating...")
    (try (up)
         (catch Exception e nil))
    component)
  (stop [component]
    component))

(defn new-migrate []
  (map->Migrate {}))

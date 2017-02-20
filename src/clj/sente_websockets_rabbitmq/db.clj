(ns sente-websockets-rabbitmq.db
  (:require
   [com.stuartsierra.component :as component]
   [sente-websockets-rabbitmq.config :refer [config]]
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
    (up)
    component)
  (stop [component]
    component))

(defn new-migrate []
  (map->Migrate {}))

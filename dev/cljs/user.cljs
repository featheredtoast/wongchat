(ns cljs.user
  (:require [sente-websockets-rabbitmq.core])
  (:use [org.clojars.featheredtoast.reloaded-repl-cljs :only [go reset stop start]]))

(defn foo []
  (println "foo"))

(def asdf "asdf")

(ns sente-websockets-rabbitmq.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [org.clojars.featheredtoast.reloaded-repl-cljs :as reloaded]
            [sente-websockets-rabbitmq.app :as app :refer [chat-system]]
            [sente-websockets-rabbitmq.views :as views :refer [main-app]]
            [reagent.core :as reagent]))

(enable-console-print!)

(reloaded/set-init-go! #(chat-system))
(reagent/render [main-app] (js/document.getElementById "app"))

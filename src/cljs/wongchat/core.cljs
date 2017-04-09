(ns wongchat.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [org.clojars.featheredtoast.reloaded-repl-cljs :as reloaded]
            [wongchat.app :as app :refer [chat-system]]
            [wongchat.views :as views :refer [main-app]]
            [rum.core :as rum]))

(enable-console-print!)

(reloaded/set-init-go! #(chat-system))
(rum/mount (main-app) (js/document.getElementById "app"))

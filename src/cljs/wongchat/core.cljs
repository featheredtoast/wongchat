(ns wongchat.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [wongchat.app :as app :refer [chat-system]]
            [wongchat.views :as views :refer [main-app]]
            [rum.core :as rum]))

(enable-console-print!)

(rum/mount (main-app) (js/document.getElementById "app"))

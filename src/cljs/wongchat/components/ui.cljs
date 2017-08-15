(ns wongchat.components.ui
  (:require [com.stuartsierra.component :as component]
            [wongchat.views :as views :refer [main-app]]
            [rum.core :as rum]))

(defn render []
  (rum/mount (main-app) (js/document.getElementById "app")))

(defrecord UIComponent []
  component/Lifecycle
  (start [component]
    (render)
    component)
  (stop [component]
    component))

(defn new-ui-component []
  (map->UIComponent {}))

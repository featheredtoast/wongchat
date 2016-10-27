(ns sente-websockets-rabbitmq.css.css
  (:require [garden.def :refer [defstylesheet defstyles]]))

(defstylesheet screen
  {:output-to "resources/public/css/style.css"}
  [:body
   {:font-family "sans-serif"
    ;;:color "red"
    }])

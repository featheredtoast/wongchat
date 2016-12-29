(ns sente-websockets-rabbitmq.css.css
  (:require [garden.def :refer [defstylesheet defstyles]]
            [garden.stylesheet :refer (at-import at-media at-keyframes)]))

(let [typing-keyframe (at-keyframes :typing
                                    [:0% {:transform "translate(0,0)"}]
                                    [:60% {:transform "translate(0,0)"}]
                                    [:100% {
                                          :transform "translate(0,-3px)"}])]
  (defstylesheet screen
    {:output-to "resources/public/css/style.css"}
    typing-keyframe
    [:body
     {:font-family "sans-serif"
      ;;:color "red"
      }]
    [:.typing-notification-container
     {:display "inline-block"
      :height "10px"}]
    [:.typing-notification
     {:color "#9d9d9d"}]
    [:.circle
     {:display "inline-block"
      :margin-left "1px"
      :margin-right "1px"
      :width "8px"
      :height "8px"
      :background "#dedede"
      :border-radius "8px"
      :animation-name "typing"
      :animation-duration "0.5s"
      :animation-direction "alternate"
      :animation-iteration-count "infinite"}]
    [:.circle2
     {:animation-delay ".1s"}]
    [:.circle3
     {:animation-delay ".2s"}]))

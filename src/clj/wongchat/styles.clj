(ns wongchat.styles
  (:require [garden-watcher.def :refer [defstyles]]
            [garden.stylesheet :refer (at-import at-media at-keyframes)]))

(def typing-keyframe
  (at-keyframes
   :typing
   [:0% {:transform "translate(0,0)"}]
   [:60% {:transform "translate(0,0)"}]
   [:100% {
           :transform "translate(0,-3px)"}]))

(defstyles style
  {:output-to "resources/public/css/style.css"}
  typing-keyframe
  [:body
   {:font-family "sans-serif"}]
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
   {:animation-delay ".2s"}]
  [:.main-menu
   {:display "block"
    :position "fixed"
    :border-right "1px solid grey"
    :width "200px"
    :height "100%"
    :z-index "1000"}]
  [:.content-mask
   {:display "block"
    :position "fixed"
    :background "grey"
    :opacity "0.5"
    :width "100%"
    :height "100%"
    :z-index "999"
    :visibility "visible"}]
  [:.content-mask-close
   {:visibility "hidden"
    :opacity "0.0"}])

(ns wongchat.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [wongchat.core-test]))

(enable-console-print!)

(doo-tests 'wongchat.core-test)

(ns sente-websockets-rabbitmq.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [sente-websockets-rabbitmq.core-test]))

(enable-console-print!)

(doo-tests 'sente-websockets-rabbitmq.core-test)

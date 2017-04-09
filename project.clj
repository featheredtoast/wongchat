(defproject sente-websockets-rabbitmq "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.40" :scope "provided"]
                 [http-kit "2.2.0"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [bk/ring-gzip "0.1.1"]
                 [ring.middleware.logger "0.5.0"]
                 [ring-middleware-format "0.7.0"]
                 [compojure "1.5.1"]
                 [environ "1.1.0"]
                 [rum "0.10.8"]
                 [org.clojure/core.async "0.2.395"]
                 [com.taoensso/sente "1.11.0"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [com.novemberain/langohr "3.6.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [com.cemerick/friend "0.2.3"]
                 [qarth "0.1.2" :exclusions [com.fasterxml.jackson.core/jackson-core]]
                 [clj-http "2.2.0"]
                 [ragtime "0.6.3"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [postgresql/postgresql "9.3-1102.jdbc41"]
                 [clj-redis-session "2.1.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [org.danielsz/system "0.4.1-SNAPSHOT"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [im.chit/hara.io.watch "2.4.8"]
                 [garden "1.3.2"]
                 [hiccup "1.0.5"]
                 [org.clojars.featheredtoast/reloaded-repl-cljs "0.1.0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.cognitect/transit-clj "0.8.297"]
                 [clj-time "0.13.0"]
                 [cljsjs/hammer "2.0.4-5"]
                 [org.bouncycastle/bcprov-jdk15on "1.56"]
                 [commons-codec/commons-codec "1.10"]
                 [org.bitbucket.b_c/jose4j "0.5.5"]
                 [clj-http "2.3.0"]
                 [nl.martijndwars/web-push "2.0.0"]
                 [bidi "2.0.16"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-environ "1.1.0"]]

  :min-lein-version "2.6.1"

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :test-paths ["test/clj" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js" "resources/public/sw.js"]

  :uberjar-name "sente-websockets-rabbitmq.jar"

  ;; Use `lein run` if you just want to start a HTTP server, without figwheel
  :main sente-websockets-rabbitmq.application

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user
                 :init (go)}

  :cljsbuild {:builds
              [{:id "app"
                :source-paths ["src/cljs" "src/cljc" "dev"]
                :figwheel {:on-jsload "org.clojars.featheredtoast.reloaded-repl-cljs/go"
                           :websocket-host :js-client-host}
                :compiler {:main cljs.user
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/sente_websockets_rabbitmq.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}
               {:id "sw"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {:main sente-websockets-rabbitmq.sw
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/sw.js"
                           :output-dir "resources/public/js/compiled/sw"
                           :optimizations :advanced
                           :source-map-timestamp true}}
               {:id "test"
                :source-paths ["src/cljs" "test/cljs" "src/cljc" "test/cljc"]
                :compiler {:output-to "resources/public/js/compiled/testable.js"
                           :main sente-websockets-rabbitmq.test-runner
                           :optimizations :none}}

               {:id "min"
                :source-paths ["src/cljs" "src/cljc"]
                :jar true
                :compiler {:main sente-websockets-rabbitmq.core
                           :output-to "resources/public/js/compiled/sente_websockets_rabbitmq.js"
                           :output-dir "target"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false}}]}

  ;; When running figwheel from nREPL, figwheel will read this configuration
  ;; stanza, but it will read it without passing through leiningen's profile
  ;; merging. So don't put a :figwheel section under the :dev profile, it will
  ;; not be picked up, instead configure figwheel here on the top level.

  :figwheel { ;; :http-server-root "public"       ;; serve static assets from resources/public/
             ;; :server-port 3449                ;; default
             ;; :server-ip "127.0.0.1"           ;; default
             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process. We
             ;; don't do this, instead we do the opposite, running figwheel from
             ;; an nREPL process, see
             ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
             ;; :nrepl-port 7888

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"
             :server-logfile "log/figwheel.log"}

  :doo {:build "test"}

  :profiles {:dev
             {:dependencies [[figwheel "0.5.8"]
                             [figwheel-sidecar "0.5.8"]
                             [com.cemerick/piggieback "0.2.1"]
                             [org.clojure/tools.nrepl "0.2.12"]
                             [lein-doo "0.1.7"]
                             [reloaded.repl "0.2.3"]]

              :plugins [[lein-figwheel "0.5.8"]
                        [lein-doo "0.1.7"]]
              :source-paths ["dev"]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}

             :uberjar
             {:source-paths ^:replace ["src/clj" "src/cljc"]
              :prep-tasks ["compile" ["cljsbuild" "once" "min"]
                           "compile" ["cljsbuild" "once" "sw"]]
              :hooks []
              :omit-source true
              :aot :all}})

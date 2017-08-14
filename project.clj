(defproject featheredtoast/wongchat "0.1.0-SNAPSHOT"
  :description "A chat app"
  :url "https://github.com/featheredtoast/wongchat"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.40" :scope "provided"]
                 [http-kit "2.2.0"]
                 [ring "1.6.2"]
                 [ring/ring-defaults "0.3.1"]
                 [bk/ring-gzip "0.2.1"]
                 [ring.middleware.logger "0.5.0"]
                 [ring-middleware-format "0.7.2"]
                 [compojure "1.6.0"]
                 [environ "1.1.0"]
                 [rum "0.10.8"]
                 [org.clojure/core.async "0.3.443"]
                 [com.taoensso/sente "1.11.0"]
                 [com.novemberain/langohr "4.0.0" :exclusions [clj-http slingshot com.fasterxml.jackson.core/jackson-core]]
                 [com.cemerick/friend "0.2.3" :exclusions [org.clojure/core.cache]]
                 [qarth "0.1.3" :exclusions [slingshot com.fasterxml.jackson.core/jackson-core]]
                 [ragtime "0.7.1"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [postgresql/postgresql "9.3-1102.jdbc41"]
                 [clj-redis-session "2.1.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [org.danielsz/system "0.4.1-SNAPSHOT"]
                 [im.chit/hara.io.watch "2.5.10"]
                 [garden "1.3.2"]
                 [hiccup "1.0.5"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.cognitect/transit-clj "0.8.300"]
                 [clj-time "0.14.0"]
                 [cljsjs/hammer "2.0.4-5"]
                 [org.bouncycastle/bcprov-jdk15on "1.57"]
                 [commons-codec/commons-codec "1.10"]
                 [org.bitbucket.b_c/jose4j "0.5.7"]
                 [clj-http "2.3.0"]
                 [nl.martijndwars/web-push "3.0.0"]
                 [bidi "2.1.1"]
                 [kibu/pushy "0.3.7"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-environ "1.1.0"]]

  :min-lein-version "2.6.1"

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :test-paths ["test/clj" "test/cljc"]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js" "resources/public/sw.js" "dev-target"]

  :uberjar-name "wongchat.jar"

  ;; Use `lein run` if you just want to start a HTTP server, without figwheel
  :main wongchat.application

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (run) and
  ;; (browser-repl) live.
  :repl-options {:init-ns user}

  :cljsbuild {:builds
              [{:id "app"
                :source-paths ["src/cljs" "src/cljc" "dev"]
                :figwheel {:on-jsload "wongchat.system/reset"
                           :websocket-host :js-client-host}
                :compiler {:main cljs.user
                           :asset-path "/js/compiled/out"
                           :output-to "dev-target/js/compiled/wongchat.js"
                           :output-dir "dev-target/js/compiled/out"
                           :source-map-timestamp true}}
               {:id "sw"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {:main wongchat.sw
                           :asset-path "/js/compiled/out"
                           :output-to "dev-target/sw.js"
                           :output-dir "dev-target/js/compiled/sw"
                           :optimizations :advanced
                           :source-map-timestamp true}}
               {:id "test"
                :source-paths ["src/cljs" "test/cljs" "src/cljc" "test/cljc"]
                :compiler {:output-to "dev-target/js/compiled/testable.js"
                           :main wongchat.test-runner
                           :optimizations :none}}

               {:id "min"
                :source-paths ["src/cljs" "src/cljc"]
                :jar true
                :compiler {:main wongchat.core
                           :output-to "resources/public/js/compiled/wongchat.js"
                           :output-dir "target/min"
                           :source-map-timestamp true
                           :optimizations :advanced
                           :pretty-print false}}

               {:id "sw-uberjar"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {:main wongchat.sw
                           :asset-path "/js/compiled/out"
                           :output-to "resources/public/sw.js"
                           :output-dir "target/sw-uberjar"
                           :optimizations :advanced
                           :source-map-timestamp true}}]}

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
             {:dependencies [[figwheel "0.5.12"]
                             [figwheel-sidecar "0.5.12"]
                             [com.cemerick/piggieback "0.2.2"]
                             [org.clojure/tools.nrepl "0.2.13"]
                             [lein-doo "0.1.7"]
                             [reloaded.repl "0.2.3"]
                             [featheredtoast/repl-watcher "0.2.1"]]

              :plugins [[lein-figwheel "0.5.12"]
                        [lein-doo "0.1.7"]]
              :source-paths ["dev"]
              :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}

             :uberjar
             {:source-paths ^:replace ["src/clj" "src/cljc"]
              :prep-tasks ["compile" ["cljsbuild" "once" "min"]
                           "compile" ["cljsbuild" "once" "sw-uberjar"]]
              :hooks []
              :omit-source true
              :aot :all}})

{:dev {:dependencies [[com.cemerick/piggieback "0.1.3"]
                      [weasel "0.3.0"]]

       :cljsbuild
       {:builds [{:id "test"
                  :source-paths ["src" "test"]
                  :compiler {:output-dir "resources/public/out/test"
                             :output-to "resources/public/js/test.js"
                             :source-map "resources/public/js/test.js.map"
                             :pretty-print true
                             :optimizations :none}}
                 {:id "dev"
                  :source-paths ["src"]
                  :compiler {:output-dir "resources/public/out/dev"
                             :output-to "resources/public/js/dev.js"
                             :source-map "resources/public/js/dev.js.map"
                             :pretty-print true
                             :optimizations :none}}
                 {:id "simple"
                  :source-paths ["src"]
                  :compiler {:output-dir "resources/public/out/simple"
                             :output-to "resources/public/js/simple.js"
                             :source-map "resources/public/js/simple.js.map"
                             :optimizations :simple}}
                 {:id "prod"
                  :source-paths ["src"]
                  :compiler {:output-dir "resources/public/out/prod"
                             :output-to "resources/public/js/prod.js"
                             :optimizations :advanced
                             :pretty-print false
                             :preamble ["react/react.min.js"]
                             :externs ["react/externs/react.js"]}}]}

       :aliases {"auto-test" ["do" "clean,"
                              "cljsbuild" "once" "test,"
                              "cljsbuild" "auto" "test"]
                 "auto-dev" ["do" "clean,"
                             "cljsbuild" "once" "dev,"
                             "cljsbuild" "auto" "dev"]}

       :repl-options {:nrepl-middleware [cider.nrepl.middleware.classpath/wrap-classpath
                                         cider.nrepl.middleware.complete/wrap-complete
                                         cider.nrepl.middleware.info/wrap-info
                                         cider.nrepl.middleware.inspect/wrap-inspect
                                         cider.nrepl.middleware.macroexpand/wrap-macroexpand
                                         cider.nrepl.middleware.stacktrace/wrap-stacktrace
                                         cider.nrepl.middleware.test/wrap-test
                                         cider.nrepl.middleware.trace/wrap-trace
                                         cider.nrepl.middleware.undef/wrap-undef
                                         cemerick.piggieback/wrap-cljs-repl]}

       :injections [(require  '[weasel.repl.websocket :as weasel]
                              '[cemerick.piggieback :as piggieback]
                              '[clojure.pprint :refer [pprint]])
                    (defn browser-repl-env []
                      (weasel/repl-env :ip "0.0.0.0" :port 9001))
                    (defn browser-repl []
                      (piggieback/cljs-repl :repl-env (browser-repl-env)))]}}

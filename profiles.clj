{:dev {:dependencies [[com.cemerick/piggieback "0.1.3"]
                      [weasel "0.3.0"]]

       :cljsbuild
       {:builds {:dev
                 {:source-paths ["src/cljs"]
                  :compiler {:preamble ["react/react.min.js"]
                             :output-to "resources/public/main.js"
                             :output-dir "resources/public/out"
                             :source-map "resources/public/main.js.map"
                             :optimizations :whitespace}}}}

       :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

       :injections [(require  '[weasel.repl.websocket :as weasel]
                              '[cemerick.piggieback :as piggieback]
                              '[clojure.pprint :refer [pprint]])
                    (defn browser-repl-env []
                      (weasel/repl-env :ip "0.0.0.0" :port 9001))
                    (defn browser-repl []
                      (piggieback/cljs-repl :repl-env (browser-repl-env)))]}}

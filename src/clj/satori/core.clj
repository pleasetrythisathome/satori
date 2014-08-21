(ns satori.core
  (:require [satori.server :refer [start-server]]
            [clojure.tools.nrepl.server :as nrepl]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [cider.nrepl.middleware classpath complete info inspect stacktrace trace]
            [cemerick.piggieback]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io])
  (:use [cfg.current]))

(def nrepl-server (atom nil))

(def nrepl-middleware `[cider.nrepl.middleware.classpath/wrap-classpath
                        cider.nrepl.middleware.complete/wrap-complete
                        cider.nrepl.middleware.info/wrap-info
                        cider.nrepl.middleware.inspect/wrap-inspect
                        cider.nrepl.middleware.macroexpand/wrap-macroexpand
                        cider.nrepl.middleware.stacktrace/wrap-stacktrace
                        cider.nrepl.middleware.test/wrap-test
                        cider.nrepl.middleware.trace/wrap-trace
                        cider.nrepl.middleware.undef/wrap-undef
                        cemerick.piggieback/wrap-cljs-repl])

(defn -main
  "Runs the application.
  Starts the application server.
  Starts a nrepl server with cider handlers."
  [& args]
  (reset! nrepl-server (nrepl/start-server :port 3001
                                           :bind "0.0.0.0"
                                           :handler (apply nrepl/default-handler (map resolve nrepl-middleware))))
  (println "nrepl listening on port 3001")
  (start-server)
  (println "http/kit server listening on PORT 8080"))

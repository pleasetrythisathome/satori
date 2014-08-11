(ns satori.core
  (:require [satori.server :refer [start-server]]
            [clojure.tools.nrepl.server :as nrepl]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [cider.nrepl.middleware classpath complete info inspect stacktrace trace]
            [cemerick.piggieback]))

(def nrepl-server (atom nil))

(defn -main
  "Runs the application.
  Starts the application server.
  Starts a nrepl server with cider handlers."
  [& args]
  (reset! nrepl-server (nrepl/start-server :port 3001
                                           :bind "0.0.0.0"
                                           :handler cider-nrepl-handler))
  (println "nrepl listening on port 3001")
  (start-server)
  (println "http/kit server listening on PORT 8080"))

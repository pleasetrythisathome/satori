(defproject satori "0.1.0-SNAPSHOT"
  :description "A clojure(script) framework"
  :url "http://example.com/FIXME"

  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "Same as Clojure"}

  :dependencies [;; clojure
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2288"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/core.match "0.2.1"]

                 ;; server
                 [http-kit "2.1.18"]
                 [compojure "1.1.8"]
                 [ring "1.3.0"]
                 [ring/ring-devel "1.3.0"]
                 [hiccup "1.0.5"]
                 [cider/cider-nrepl "0.8.0-SNAPSHOT"]

                 ;; om
                 [om "0.7.1"]
                 [sablono "0.2.21"]
                 [ankha "0.1.3"]

                 [rewrite-clj "0.3.9"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [slothcfg "1.0.1"]
            [lein-plz "0.1.1"]
            [lein-templater "0.1.0-SNAPSHOT"]]

  :hooks [leiningen.cljsbuild]

  :source-paths ["src/clj"]
  :resource-paths ["resources"]
  :main satori.core

  :template {:output-dir "lein-template"
             :title "satori"
             :version "0.1.0"
             :project {:description "A leiningen template for Satori"
                       :url "http://www.github.com/"}
             :readme "resources/README.template.md"
             :msg "There is no beginning or ending, just change."
             :file-overrides {"README.md" "resources/README.template.md"}})

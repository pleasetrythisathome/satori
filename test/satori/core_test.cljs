(ns satori.core-test
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var]])
  (:require [cemerick.cljs.test :as t]
            [clojure.string :as str]

            [goog.dom :as gdom]
            [crate.core :as crate]
            [shodan.console :as c]

            [figwheel.client :as fw]))

(deftest app
  (is (= 1 1)))

(defn test-println
  [output-el]
  (fn [line]
    (when-let [line (str/replace line #"\\n" "")]
      (let [el (crate/html
                [:div {:class (cond
                               (re-find #"ERROR" line) "orange"
                               (re-find #"FAIL" line) "red"
                               (re-find #"Testing complete" line) "green"
                               :else "")}
                 line])]
        (gdom/appendChild output-el el)))))

(defn ^:export run
  "runs the test suite"
  []
  (let [output (gdom/getElement "output")]
    (gdom/setTextContent output "")
    (t/set-print-fn! (test-println output))
    (t/run-all-tests)))

(run)

(fw/watch-and-reload
 :jsload-callback (fn []
                    (run)
                    ))

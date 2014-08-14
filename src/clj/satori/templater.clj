(ns satori.templater
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :refer [pprint]]
            [clojure.walk :as w])
  (:use [cfg.current]))

;; ===== general utils =====

(defn sanitize
  "Replace hyphens with underscores."
  [s]
  (str/replace s "-" "_"))

(defn symbol-first?
  "returns true if sym is the first item in seq"
  [sym seq]
  (and (seq? seq)
       (-> seq first
           (= sym))))

;; ===== file io =====

(defn read-all
  "reads all lines from a file and returns a sequence of forms"
  [path]
  (let [r (java.io.PushbackReader.
           (clojure.java.io/reader path))]
    (let [eof (Object.)]
      (take-while #(not= % eof) (repeatedly #(read r false eof))))))

(defn spit-seq
  "spits all the items in a seq concatted into a file"
  [path forms]
  (doall (map-indexed (fn [idx form]
                        (spit path (pr-str form) :append (not (zero? idx))))
                      forms)))

(defn gitignored?
  "returns true if the file would be ignored by git"
  [file]
  (not (str/blank? (->> file
                        .getAbsolutePath
                        (sh "git" "check-ignore")
                        :out))))

(defn unhide
  "removes a prefixed . if it exists"
  [filename]
  (cond-> filename
          (= \. (first filename)) (subs 1)))

(defn get-project-files
  "returns all files in the project that are not ignored by git"
  []
  (->> @project
       :root
       io/file
       file-seq
       (filter (complement gitignored?))
       (filter #(.isFile %))))

;; ===== lein utils =====

(defn replace-template-var
  "replace a matched string with a lein template variable"
  [s match var]
  (str/replace s match (str "{{" var "}}")))

(defn fresh-template [title dir]
  (sh "lein" "new" "template" title "--to-dir" dir)
  (sh "rm" (str dir "/src/leiningen/new/" title "/foo.clj")))

;; ===== file processors =====

(defn build-renderers [files root name]
  (map (juxt (fn [file]
               (-> file
                   .getAbsolutePath
                   (str/replace root "")
                   (replace-template-var (sanitize name) "sanitized")))
             (fn [file]
               (seq ['render (unhide (.getName file)) 'data])))
       files))


(defn create-template []
  (let [{:keys [name root template]} @project
        {:keys [output-dir title project msg]} template

        target-dir (str root "/" output-dir)
        template-dir (str target-dir "/src/leiningen/new/")
        src-dir (str template-dir title "/")
        renderer-path (str template-dir title ".clj")

        files (get-project-files)
        renderer (w/postwalk (fn [form]
                               (cond
                                (symbol-first? '->files form) (concat (butlast form) (build-renderers files root name))
                                (symbol-first? 'main/info) (let []
                                                             (concat (butlast form) [msg]))
                                :else form))
                             (-> renderer-path
                                 slurp
                                 read-all))]

    (sh "lein" "new" "template" title "--to-dir" target-dir)
    (sh "rm" (str src-dir "foo.clj"))
    (spit-forms renderer-path renderer)

    (doseq [file files]
      (spit (str src-dir (get-file-name file)) (replace-project-name file name)))))

(create-template)

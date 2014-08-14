(ns satori.templater
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :refer [pprint]]
            [clojure.walk :as w])
  (:use [cfg.current]))

(defn read-all
  [filename]
  (let [r (java.io.PushbackReader.
           (clojure.java.io/reader filename))]
    (let [eof (Object.)]
      (take-while #(not= % eof) (repeatedly #(read r false eof))))))

(defn not-gitignored? [file]
  (str/blank? (->> file
                   .getAbsolutePath
                   (sh "git" "check-ignore")
                   :out)))

(defn get-project-files []
  (->> @project
       :root
       io/file
       file-seq
       (filter not-gitignored?)
       (filter #(.isFile %))))

(defn replace-project-name [file name]
  (-> file
      .getAbsolutePath
      slurp
      (str/replace name "{{name}}")))

(defn sanitize
  "Replace hyphens with underscores."
  [s]
  (str/replace s "-" "_"))

(defn get-render-path [file root name]
  (-> file
      .getAbsolutePath
      (str/replace root "")
      (str/replace (sanitize name) "{{sanitized}}")))

(defn get-file-name [file]
  (let [name (.getName file)]
    (if (= \. (first name))
      (subs name 1)
      name)))

(defn spit-forms [path forms]
  (doall (map-indexed (fn [idx form]
                        (spit path (pr-str form) :append (not (zero? idx))))
                      forms)))

(defn build-renderers [files root name]
  (map (juxt #(get-render-path % root name)
             #(seq ['render (get-file-name %) 'data]))
       files))

(defn symbol-first? [sym seq]
  (and (seq? seq)
       (-> seq first
           (= sym))))

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

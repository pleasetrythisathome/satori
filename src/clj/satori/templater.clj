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

(defn get-rel-path [file]
  (-> file
      .getAbsolutePath
      (str/replace (str (:root @project) "/") "")))

(defn is-overridden?
  "takes either keys or vals from file-overrides and returns a function that
returns true if the file has an override path defined in the template project settings"
  [f]
  (fn [file]
    (let [{:keys [root template]} @project]
      (contains? (set (f (:file-overrides template))) (get-rel-path file)))))

(defn get-project-files
  "returns all files in the project that are not ignored by git"
  []
  (let [{:keys [root template]} @project]
    (->> root
         io/file
         file-seq
         (filter (complement gitignored?))
         (filter (complement (is-overridden? vals)))
         (filter #(.isFile %)))))

;; ===== lein utils =====

(defn replace-template-var
  "replace a matched string with a lein template variable"
  [s match var]
  (str/replace s match (str "{{" var "}}")))

(defn fresh-template [title dir]
  (sh "rm" "-rf" dir)
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

(defmulti process-file (is-overridden? keys))

(defmethod process-file false [file]
  (-> file
      .getAbsolutePath
      slurp
      (replace-template-var (:name @project) "name")))

(defmethod process-file true [file]
  (process-file (io/file (get-in @project [:template :file-overrides (get-rel-path file)]))))

(defn create-renderer-visitor [form]
  (let [{:keys [name root] :as project} @project]
    (cond
     (symbol-first? '->files form) (concat (butlast form) (build-renderers (get-project-files) root name))
     (symbol-first? 'main/info form) (concat (butlast form) [(get-in project [:template :msg])])
     :else (identity form))))

;; ===== let's make templates! =====

(defn create-template []
  (let [{:keys [name root template] :as proj} @project
        {:keys [output-dir title]} template

        target-dir (str root "/" output-dir)
        lein-dir (str target-dir "/src/leiningen/new/")
        src-dir (str lein-dir title "/")
        renderer-path (str lein-dir title ".clj")]

    (fresh-template title target-dir)

    (spit-seq renderer-path (w/postwalk create-renderer-visitor (read-all renderer-path)))

    (doseq [file (get-project-files)]
      (spit (str src-dir (unhide (.getName file))) (process-file file)))))

;; (create-template)

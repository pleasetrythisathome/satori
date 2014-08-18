(ns satori.templater
  (:require [clojure.string :as str]
            [clojure.core.match :as m]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :refer [pprint]]
            [clojure.walk :as w]
            [rewrite-clj.zip :as z]
            [rewrite-clj.parser :as p]
            [rewrite-clj.printer :as prn])
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
  [file loc]
  (let [{:keys [root template]} @project
        f (condp = loc
            :project vals
            :template keys)]
    (contains? (set (f (:file-overrides template))) (get-rel-path file))))

(defn get-override
  "returns the overwritten file defined in :template in project"
  [file]
  (->> file
       get-rel-path
       (conj [:template :file-overrides])
       (get-in @project)
       io/file))

(defn file-or-override
  "returns either the file or its defined override"
  [file]
  (cond-> file
          (is-overridden? file :template) get-override))

(defn get-project-files
  "returns all files in the project that are not ignored by git"
  []
  (let [{:keys [root template]} @project]
    (->> root
         io/file
         file-seq
         (filter (complement gitignored?))
         (filter #(not (is-overridden? % :project)))
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

;; ===== make core.match compatible with regex =====

(defrecord RegexPattern [regex])

(defmethod m/emit-pattern java.util.regex.Pattern
  [pat]
  (RegexPattern. pat))

(defmethod m/to-source RegexPattern
  [pat ocr]
  `(re-find ~(:regex pat) ~ocr))

;; ===== zipper utils =====

(defn print-zipper
  "prints a rewrite-clj zipper as a string"
  [zipper]
  (with-out-str (z/print-root zipper)))

(defn zip-down-up
  "steps into zipper, applys f, and then recreates zipper from root"
  [f zipper]
  (-> zipper
      z/down
      f
      print-zipper
      z/of-string))

;; ===== file processors =====

(defn modify-proj
  "converts a proj file form sequence into a project map, applies f to it, and then prints it back to a string"
  [f proj-seq]
  (let [[info props] (split-at 3 (first proj-seq))]
    (->> props
         (apply hash-map)
         f
         (mapcat identity)
         (concat info)
         (conj '()))))

(defmulti processer (fn [file] (.getName file)))

(defmethod processer :default [file] identity)

(defmethod processer "project.clj" [file]
  (let [overrides (get-in @project [:template :project])]
    (->> file
         (modify-proj #(apply dissoc % [:template]))
         (modify-proj #(merge % overrides)))))

(defn build-renderer
  "builds a vector of file renderers"
  [files]
  (let [{:keys [root name] :as project} @project]
    (map (juxt (fn [file]
                 (-> file
                     .getAbsolutePath
                     (str/replace (str root "/") "")
                     (replace-template-var (sanitize name) "sanitized")))
               (fn [file]
                 (seq ['render (unhide (.getName file)) 'data])))
         files)))

(defn process-renderer
  "process the template renderer"
  [root]
  (-> root
      (zip-down-up #(-> %
                        (z/find-value z/next 'main/info)
                        z/right
                        (z/replace (get-in @project [:template :msg]))))
      (zip-down-up #(-> %
                        z/right
                        (z/find-value z/next '->files)
                        z/up
                        (z/edit (fn [form]
                                  (concat (butlast form)
                                          (build-renderers (get-project-files)))))))))

(defn process-file [file]
  (let [path (.getAbsolutePath file)
        [type & name] (-> (.getName file)
                          (str/split #"\.")
                          reverse)
        name (str/join "." (reverse name))
        templated (-> path
                      slurp
                      (replace-template-var (:name @project) "name"))]
    (m/match [type]
             [#"clj"] (-> templated
                          (#(str "(" % "\n)"))
                          read-zipper
                          ((fn [root]
                             (condp = name
                               "project" root
                               (get-in @project [:template :title]) (process-renderer root)
                               root)))
                          print-zipper
                          (#(subs # 1 (- (count #) 3))))
             :else templated)))

(->> "/lein-template/src/leiningen/new/satori.clj"
     (str (:root @project))
     slurp
     read-zipper

     z/sexpr
     )

;;(pprint (process-file (nth (get-project-files) 2)))

;; ===== let's make templates! =====

(defn templater []
  (let [{:keys [name root template] :as proj} @project
        {:keys [output-dir title]} template

        target-dir (str root "/" output-dir)
        lein-dir (str target-dir "/src/leiningen/new/")
        src-dir (str lein-dir title "/")
        renderer-path (str lein-dir title ".clj")]

    (fresh-template title target-dir)

    (doseq [[path file] (cons [renderer-path (io/file renderer-path)]
                              (map (juxt (fn [file]
                                           (->> file
                                                .getName
                                                unhide
                                                (str src-dir)))
                                         file-or-override)
                                   (get-project-files)))]
      (spit path (process-file file)))))

(comment (templater)

         (def path (->> "/lein-template/src/leiningen/new/satori.clj"
                        (str (:root @project))))

         (with-out-str (->> path
                            read-all
                            (w/postwalk process-renderer-step)
                            pprint))

         (def data (z/of-string (->> path
                                     read-all
                                     pr-str)))

         (z/sexpr data)

         (pprint data)

         (-> data
             (z/find 'main/info)
             z/sexpr)

         (-> data
             z/down
             (z/find-value z/right 'defn)
             z/sexpr)

         (z/find-value data z/next '->files)
         )

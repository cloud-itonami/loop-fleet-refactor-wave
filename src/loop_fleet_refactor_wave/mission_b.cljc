(ns loop-fleet-refactor-wave.mission-b
  "Candidate-discovery heuristics for Mission B: migrate one kotoba/app
   vertical slice per repo per wave from .clj/.cljc to .kotoba.

   The acceptance criterion and migration procedure are NOT owned here --
   they are superproject ADR-2608261100 and the `kotoba-clj-to-kotoba`
   skill. These functions only narrow a large repo set to a short candidate
   list of small, self-contained, low-risk files; the actual 4-way
   classification (portable pure / portable effectful app / host mechanism
   / operational script) is applied by the dispatched agent reading the
   real file, not automated here.

   Pure, portable across clj/cljs/nbb. No I/O -- callers read files and pass
   text/paths in."
  (:require [clojure.string :as str]))

(defn custody-gated?
  "True if `script-text` (from a repo's docs/ or scripts/ *.cljs file) is a
   custody-verification contract -- i.e. it greps for migration.edn or
   svelte/ in a verification context. Mirrors svelte-cljs-wave-tick.cljs's
   custody-gated? logic and its documented lesson: judge by content, not by
   a fixed filename (verify-custody.cljs / verify-docs-claims.cljs /
   check-migration-identity.cljs are all real names in this workspace, and
   more will exist that we have not seen yet)."
  [script-text]
  (boolean (re-find #"(?i)(migration\.edn|svelte/)" (or script-text ""))))

(defn kotoba-twin-exists?
  "True if a .kotoba file already exists for the given .clj/.cljc relative
   path (compares the file stem against the given collection of .kotoba
   relative paths in the same repo). A candidate whose migration is already
   done should not be re-proposed."
  [clj-relative-path kotoba-relative-paths]
  (let [stem (some-> clj-relative-path (str/replace #"\.cljc?$" ""))]
    (boolean (some #(str/includes? % stem) kotoba-relative-paths))))

(defn host-mechanism-signal?
  "Heuristic: does `source-text` show signs of socket / filesystem / process
   / credential access that mark it as host mechanism rather than portable
   product-decision logic? A positive match does not exclude the whole
   file -- it means the dispatched agent must separate the decision core
   from the mechanism, per the kotoba-clj-to-kotoba skill's 'guest / host'
   split; it just means this file is not a trivial first-wave candidate."
  [source-text]
  (boolean
   (re-find #"(?i)(clj-http|hato\.client|java\.net\.Socket|fs/readFileSync|fs/writeFileSync|process/exec|System/getenv|clojure\.java\.io|\.execFileSync|\.spawnSync)"
            (or source-text ""))))

(defn candidate-slice?
  "A file is a Mission B first-wave candidate when it: is not custody-gated,
   has no existing .kotoba twin, shows no host-mechanism signal, and is
   small enough to review and land as one bounded slice in a single wave."
  [{:keys [line-count custody-gated? has-kotoba-twin? host-mechanism?]}]
  (and (not custody-gated?)
       (not has-kotoba-twin?)
       (not host-mechanism?)
       (pos? line-count)
       (<= line-count 400)))

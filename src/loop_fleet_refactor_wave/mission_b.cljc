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
   (re-find #"(?i)(clj-http|hato\.client|babashka\.http-client|babashka\.process|babashka\.fs|java\.net\.Socket|fs/readFileSync|fs/writeFileSync|process/exec|System/getenv|System/exit|clojure\.java\.io|\.execFileSync|\.spawnSync|\bslurp\b|\bspit\b)"
            (or source-text ""))))

(defn operational-script-signal?
  "Heuristic: does `path` or `source-text` mark the file as an operational
   test-runner/build script rather than product-decision logic? Real
   candidates found 2026-08-29 by an earlier, less careful version of this
   predicate: 4-line `run_tests.clj` files (`(require ...) (apply
   clojure.test/run-tests ...) (System/exit 1)`) ranked as the SMALLEST --
   therefore highest-priority -- candidates under the old size-only sort,
   crowding out real decision-core files like a 121-line pure
   state-transition namespace. `line-count <= 400` alone does not separate
   'small and pure' from 'small and empty of decisions'; requiring at least
   one `defn` (below) and rejecting the well-known runner basenames here
   both do."
  [path source-text]
  (or (boolean (re-find #"(?i)(run[_-]?tests?|test[_-]?runner|runner)\.(clj|cljc)$" (or path "")))
      (boolean (re-find #"clojure\.test/run-tests|cljs\.test/run-tests" (or source-text "")))))

(defn host-boundary-path?
  "True if `path` sits on a declared host boundary -- a `host` directory or
   namespace segment. ADR-2607279200's four-way split names `kotoba/host` as
   the profile that KEEPS ambient authority, so a file the repo itself filed
   under host/ is announcing it is mechanism, not portable product semantics.

   Found 2026-08-29 by production run: `tadori/src/tadori/host/http.clj` is
   7 lines, has one defn, and reads `\"Network authority terminates here.\"`
   in its own docstring -- yet `host-mechanism-signal?` missed it because it
   delegates to `babashka.http-client` (aliased `http`), which the source
   regex did not list. Path is the more robust signal than any enumeration of
   client libraries, because the enumeration can never be complete."
  [path]
  (boolean (re-find #"(?:^|/)host/|\.host\." (or path ""))))

(defn unactivated-scaffold?
  "True if EVERY `defn` in `source-text` has an unconditional `throw` as the
   first form of its body -- an R0 scaffold that is not activated yet. Such a
   file has no product semantics to migrate: porting it would produce a
   .kotoba function returning a constant `[:result _ E]`, which is a diff with
   zero behaviour that nonetheless counts as a landed migration.

   Found 2026-08-29 by production run: three `hikari/cells/*/state_machine.cljc`
   files (`(defn solve [_state] (throw (ex-info \"R0 scaffold ... not
   activated\" ...)))`) occupied three of the four candidate slots. 111 files
   of this shape sit inside the tick's line-count range fleet-wide, and because
   they can never legitimately land they would be re-proposed every wave
   forever -- the loop would spin without advancing.

   This is the same defect class `operational-script-signal?` documents
   ('small and empty of decisions'), reached by a different route, so the
   discriminator is deliberately narrow: the throw must be the FIRST body form.
   A defensive `(throw ...)` nested in `when-not`/`cond` inside real logic does
   not match. Verified 2026-08-29 in both directions against real files --
   the three scaffolds above match; `hikari`'s `grid_edge` and
   `solar_pv_install` state machines, which are genuine ported decision cores
   carrying three defensive throws between them, do not."
  [source-text]
  (let [src (or source-text "")
        defns (count (re-seq #"\(defn-?\s" src))
        throw-first (count (re-seq #"\(defn-?\s+[^\s\[\]]+\s+(?:\^\S+\s+)?(?:\"(?:[^\"\\]|\\.)*\"\s+)?\[[^\]]*\]\s*\(throw[\s(]" src))]
    (and (pos? defns) (= defns throw-first))))

(defn strip-ns-form
  "Drop the leading `(ns ...)` form so that its `:require` aliases are not
   counted as calls by `decision-free-passthrough?`."
  [source-text]
  (str/replace (or source-text "") #"(?s)^.*?\(ns\s.*?\n\n" ""))

(defn decision-free-passthrough?
  "True if every function in `source-text` is a pass-through into another
   namespace and the file makes no decision of its own -- no branch, no
   comparison, no arithmetic, no collection reduction. Such a file is a
   configuration/adapter wrapper: its product semantics live in the namespace
   it delegates to, not here.

   Porting one cannot produce a migration. Either the delegate is still .cljc,
   in which case the workspace rule 'do not migrate a surface whose dependency
   is still .cljc' forbids it outright, or the port reproduces the same
   pass-through -- a diff with zero behaviour that nonetheless counts as a
   landed slice.

   Found 2026-08-30 by production run. The wave surfaced exactly one candidate,
   `mio/src/mio/methods/social.cljc` (13 lines, two defns, both
   `(publication/... config args)` into `etzhayyim.social.publication`, which
   has no .kotoba twin anywhere in the fleet). Measured across
   orgs/cloud-itonami: 658 files survive every other predicate in this
   namespace and still match this one, and ALL TWENTY of the smallest
   survivors are of this shape. Because `mission-b-candidates` sorts by
   ascending line count, this class does not merely appear in the queue -- it
   occupies the entire front of it, so every wave would draw its full slate
   from files that cannot legitimately land, and would redraw them forever.

   Third instance of the defect class `operational-script-signal?` names
   ('small and empty of decisions'), after `unactivated-scaffold?`. Small,
   pure and self-contained is not the same property as carrying a decision.

   Verified 2026-08-30 in both directions against real files. Matches:
   `mio/methods/social.cljc`; the 13-line `cells/social_post/state_machine.cljc`
   adapters in amime / kaname / kenchi / kuni-umi / actor-hoshimori;
   `infra-utility-connect`'s four 12-line `cells/*/cell.cljc`; and
   `cloud-itonami-app/src/cloud/itonami/app/health.cljc`, whose own docstring
   reads \"decide nothing\" because it is the host half of a migration that
   already happened. Does not match: `hikari`'s `grid_edge` and
   `solar_pv_install` state machines (the same negative controls
   `unactivated-scaffold?` uses), nor `mio`'s own `reward.cljc` and
   `analyze.cljc`, which delegate but also decide."
  [source-text]
  (let [src (strip-ns-form source-text)
        defns (count (re-seq #"\(defn-?\s" src))
        ns-calls (count (re-seq #"\([a-zA-Z][a-zA-Z0-9_.\-]*/[a-zA-Z0-9_.\-!?*<>=+]+[\s)]" src))
        decisions (count (re-seq #"\((?:if|if-not|if-let|when|when-not|when-let|cond|condp|case|and|or|not|=|not=|<|>|<=|>=|\+|-|\*|/|min|max|count|filter|remove|reduce|some|every\?)[\s)]" src))]
    (and (pos? defns) (pos? ns-calls) (zero? decisions))))

(defn defn-count
  "How many `defn`/`defn-` forms `source-text` contains. A file with zero is
   not product-decision logic -- it is requires, data, or glue."
  [source-text]
  (count (re-seq #"\(defn-?\s" (or source-text ""))))

(defn candidate-slice?
  "A file is a Mission B first-wave candidate when it: is not custody-gated,
   has no existing .kotoba twin, shows no host-mechanism signal, does not sit
   on a declared host boundary, is not an operational test-runner/build
   script, is not an unactivated R0 scaffold, is not a decision-free
   pass-through wrapper, defines at least one function, and is small enough to
   review and land as one bounded slice in a single wave."
  [{:keys [line-count custody-gated? has-kotoba-twin? host-mechanism?
           operational-script? host-boundary? unactivated-scaffold?
           decision-free-passthrough? defn-count]}]
  (and (not custody-gated?)
       (not has-kotoba-twin?)
       (not host-mechanism?)
       (not operational-script?)
       (not host-boundary?)
       (not unactivated-scaffold?)
       (not decision-free-passthrough?)
       (pos? (or defn-count 0))
       (pos? line-count)
       (<= line-count 400)))

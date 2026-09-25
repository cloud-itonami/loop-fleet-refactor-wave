# Operator quickstart — loop-fleet-refactor-wave

Three steps, each walked on 2026-09-26 from a superproject checkout
(`com-junkawasaki/root`, with `orgs/` populated by west). `ROOT` is that
checkout; adjust it if yours lives elsewhere.

```bash
ROOT=$HOME/github/com-junkawasaki
```

## 1. Run this repo's tests

The heuristics require `kotoba.lang.text` (not `clojure.string`), and kbb
resolves no git coordinates from `deps.edn`, so the `text` checkout has to be
on the classpath explicitly. Without it the run stops at
`Could not find namespace: kotoba.lang.text` — that is "did not run", not a
test failure.

```bash
cd $ROOT/orgs/cloud-itonami/loop-fleet-refactor-wave
kbb --backend sci --classpath "src:test:$ROOT/orgs/kotoba-lang/text/src" run_tests.cljk
```

Expected: `Ran 15 tests containing 74 assertions.` / `0 failures, 0 errors.`
and exit 0. If `orgs/kotoba-lang/text` is missing, fetch it first:
`west update --fetch smart text`.

## 2. Ask a heuristic about a real file

The namespaces are pure: they take already-read text, so the caller reads the
file. A throwaway probe (keep it in `$TMPDIR`, not in the repo):

```bash
cat > $TMPDIR/lfrw-probe.cljk <<'PROBE'
(ns probe (:require [loop-fleet-refactor-wave.mission-a :as a]
                    [loop-fleet-refactor-wave.mission-b :as b]
                    ["fs" :as fs]))
(let [[readme src] *command-line-args*]
  (prn {:decentralization-claim? (a/decentralization-claim? (str (fs/readFileSync readme)))
        :defn-count (b/defn-count (str (fs/readFileSync src)))}))
PROBE
kbb --backend sci --classpath "src:$ROOT/orgs/kotoba-lang/text/src" \
  $TMPDIR/lfrw-probe.cljk README.md src/loop_fleet_refactor_wave/mission_b.cljk
```

Expected: `{:decentralization-claim? true, :defn-count 11}` — this README uses
the word "decentralized", and `mission_b.cljk` has 11 `defn` forms. Point the
two arguments at any other repo's README / source file to classify it.

## 3. Run the measuring tick (superproject side)

The tick that the resident loop runs lives in the superproject, not here. It
does not `require` this repo: it carries a verbatim copy of the predicates so
that it can still exit 2 ("could not measure") on a machine where this repo is
not checked out. **When you change a predicate here, change the copy in
`scripts/fleet-refactor-wave-tick.cljk` in the same change.**

```bash
cd $ROOT
kbb --backend sci --classpath ".:scripts/nbb_compat" \
  scripts/fleet-refactor-wave-tick.cljk --limit 2 --mission a
```

Expected (about 20 s): `SCANNED<TAB>n files under orgs/cloud-itonami`,
`POOL<TAB>n repos`, `MISSION-A-RAW`, `CANDIDATES`, and exit 0. On 2026-09-26
this printed 1925 repos and 0 Mission A candidates. Exit 2 means the pool
could not be read (no `orgs/cloud-itonami`, `find` failed, or 0 repos) — do
not read it as "no candidates".

Side effect: each run appends one line to
`~/.itonami/fleet-refactor-wave-tick.ledger.edn`. It dispatches nothing and
calls no model; dispatch is the loop's job
(`scripts/fleet-refactor-wave-loop.cljk`, launchd label
`cloud.itonami.bot.fleet-refactor-wave`, procedure in the skill
`.claude/skills/fleet-refactor-wave/SKILL.md`).

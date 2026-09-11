# loop-fleet-refactor-wave

Continuous orchestrator (`loop-` role prefix, `manifest/repository-rules.edn`
`:execution :resident`) that advances one bounded wave of refactoring per
tick across the 100+ independent git repositories under
`orgs/cloud-itonami/` in the `com-junkawasaki/root` superproject.

This repository does **not** own domain scoring truth (per the `loop-`
taxonomy's `:must-not :own-domain-scoring-truth`). It owns two things only:

1. **Candidate-discovery heuristics** (`src/loop_fleet_refactor_wave/`) — pure,
   portable `.cljc` predicates that narrow a large repo set down to a short
   list of plausible candidates. These are heuristics for *discovery*, not
   the final acceptance judgment.
2. **A record of what happened** — wave outcomes are recorded in the
   superproject's ADR (`90-docs/adr/2608290100-loop-fleet-refactor-wave-scaffold.edn`)
   and in the tick/loop ledgers under `~/.gftd/` on the operating machine,
   mirroring the `svelte-cljs-wave` pattern this actor is modeled on.

The actual acceptance criteria live elsewhere and are **not redefined here**:

- **Mission A** (eliminate single-vendor conditional-write premises — e.g.
  Cloudflare D1 — from paths that self-identify as decentralized/blockchain)
  — criterion is superproject ADR-2608039000. The test is operational:
  "if you delete this store right now, does data get lost or correctness
  break?" If yes and the path claims decentralization, it's in scope.
- **Mission B** (migrate one `kotoba/app` vertical slice per repo per wave
  from `.clj`/`.cljc` to `.kotoba`) — criterion is superproject
  ADR-2608261100 and the `kotoba-clj-to-kotoba` skill. The migration unit is
  one bounded slice (state → effect → event → governor → UI → checkpoint),
  never a whole repo.

## What actually drives the loop

The operational orchestration (measure → dispatch fresh agents → advance
west pins → re-measure) lives in the **superproject**, not here, mirroring
`svelte-cljs-wave`:

- `scripts/fleet-refactor-wave-tick.cljs` — measures candidates deterministically,
  requiring this repo's classifier namespaces off its own checked-out
  `orgs/cloud-itonami/loop-fleet-refactor-wave/src` classpath entry.
- `.claude/skills/fleet-refactor-wave/SKILL.md` — the wave procedure.
- `scripts/com.gftd.fleet-refactor-wave.plist` — the launchd resident job.

This split exists because the orchestration script needs filesystem/network
I/O (find, git, gh) that has no business being "portable domain logic," while
the classification heuristics below are pure and testable in isolation, and
reusable if a second tool ever wants the same candidate judgment.

## Namespaces

- `loop-fleet-refactor-wave.mission-a` — decentralization self-claim
  detection + single-vendor-conditional-write-as-arbiter heuristics.
- `loop-fleet-refactor-wave.mission-b` — custody-gate detection + small
  self-contained decision-core file heuristics.

Both are pure `#?(:clj :cljs :cljc)`-portable functions: no I/O, no host
interop, string/collection operations only. They take already-read file
contents (README text, source text, path lists) as arguments; the tick
script owns reading the filesystem.

## Non-goals

- This repo does not itself perform refactors. Dispatched fresh agents do
  the actual work in the target `cloud-itonami` repos, each in their own
  fresh clone/worktree, following the referenced ADRs and skills directly.
- This repo does not maintain its own list of "which repos are decentralized"
  or "which files are safe to migrate" as a static registry — that would
  duplicate and drift from the target repos' own READMEs and source, which
  are the actual evidence. Every tick re-measures from scratch.

## Testing

```bash
nbb --classpath src:test run_tests.cljk
```

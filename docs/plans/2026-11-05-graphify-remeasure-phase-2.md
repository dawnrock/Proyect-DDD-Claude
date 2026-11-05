# Plan: Measure Graphify again on the phase-2 code

- **Issue:** [#71](https://github.com/dawnrock/Proyect-DDD-Claude/issues/71)
- **Type:** refactor (tooling measurement; no application code changes)
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-05
- **Status:** draft
- **Total estimate:** ~70 min (7 tasks)

## Goal

ADR 0010 (#57) found that Graphify saved tokens on dependency questions (−27 %) but cost more on general searches, so it was limited to `affected`/`path` in `buscador-referencias` and `plan-feature`. The code has grown since then (89 → 125 Java files from `v1-monolito` to `Hexagonal`, with ports, adapters and events that make "who depends on what" questions more common). This plan repeats the measurement with the same method on `Hexagonal` and records whether to keep the current use or widen it.

What must **not** change: the application, the build, `CLAUDE.md` and `.claude/`. If the result suggests widening the use of the graph, the change to `buscador-referencias`/`plan-feature` is only **proposed** to the user (it touches `.claude/`), in a later issue.

### Out of scope

- Changing `.claude/` (agents, skills, hooks) or `CLAUDE.md`: only a proposal, with the user's approval, in its own issue.
- Running Graphify in CI or versioning `graphify-out/`.
- Adding `docs/` to the graph (it would be extracted by the model and cost tokens on every rebuild, ADR 0010).

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| tooling | `scripts/measure-graphify.mjs` (new) | Runs the fixed questions against `buscador-referencias` in new headless sessions and prints the tokens of each run, so the measurement can be repeated |
| docs | `docs/adr/0010-graphify-token-savings.md` | New section "Measurement on phase 2 (2026-11-05)" with the table and the conclusion |
| docs | `docs/ia/arquitectura-ia.md`, `docs/ia/*` | Code-graph row and project status; the Graphify pending item is resolved |

**Method (same as #57, ADR 0010):** each question in a new headless session, `claude -p --agent buscador-referencias --model haiku --output-format json`, so the agent definition is loaded fresh; tokens processed = input + cache creation + cache read + output, from the JSON `usage`. Single runs are noisy (#57), so each question runs **twice** per configuration and the table shows both runs and the mean.

**Configurations:**

| | Graph | Agent |
| - | ----- | ----- |
| A · no graph | no `graphify-out/` | `buscador-referencias` as it is (falls back to Grep) |
| B · current rule | `graphify-out/` built | `buscador-referencias` as it is (graph only for dependencies) |
| C · graph first | `graphify-out/` built | a variant passed inline with `--agents` (graph first for every code search, as #57 tried, without hooks); `.claude/agents/` is not touched |

**Questions** (the three of #57, adapted to phase-2 names where the old one no longer makes sense, plus two new ones):

1. Where is `CheckingAccount.withdraw` used? (same as #57: ambiguous name, the method is `tryWithdraw`)
2. Which classes depend on `MoneyTransferService`? (same as #57)
3. Which input adapters use `CheckingAccountCommands`? (phase-2 version of "which `presentation` classes use `CheckingAccountService`")
4. Which class implements `DomainEventPublisher`, and who calls it? (new, dependency question across a port)
5. Where is rule R20 (high net worth threshold) defined, tested and documented? (new, general search over code and `docs/`)

Graphify is not installed in the cloud session: it is installed with `pip install --user graphifyy` (PyPI is reachable) and run through `scripts/graphify.mjs`, which already falls back to `python3 -m graphify`.

## Tasks

### [ ] T1 — Install Graphify and build the graph (~5 min)

- **Layer:** tooling
- **Files:** — (`graphify-out/` is ignored)
- **Depends on:** —
- **What to do:**
  1. `pip install --user graphifyy==0.9.97` (the #57 version, for comparability; if it is no longer available, the latest, recorded in the ADR). `node scripts/graphify.mjs update .` and check `affected "MoneyTransferService"` answers with file and line.
- **Acceptance criteria:**
  - [ ] `graphify-out/graph.json` exists and is not tracked (`git status` clean); the version is noted.

### [ ] T2 — Measurement script (~10 min)

- **Layer:** tooling
- **Files:** `scripts/measure-graphify.mjs`
- **Depends on:** T1
- **What to do:**
  1. Node script (no dependencies, like the other scripts): takes the configuration (`A`/`B`/`C`) and the number of runs, runs each question with `claude -p` as above (for C, the agent variant through `--agents`), parses the JSON `usage` and prints one line per run plus the mean per question. For A it moves `graphify-out/` aside and restores it at the end, even on failure.
  2. Try it with one question and one run.
- **Acceptance criteria:**
  - [ ] One run prints its tokens and the answer is complete; `graphify-out/` is back after an A run.

### [ ] T3 — Configuration A: no graph (~10 min)

- **Layer:** docs (measurement notes)
- **Files:** notes for T6
- **Depends on:** T2
- **What to do:**
  1. `node scripts/measure-graphify.mjs A 2`; keep the output and check each answer is complete (same files as a manual Grep).
- **Acceptance criteria:**
  - [ ] 10 runs recorded, with the exact questions.

### [ ] T4 — Configuration B: current rule (~10 min)

- **Layer:** docs (measurement notes)
- **Files:** notes for T6
- **Depends on:** T2
- **What to do:**
  1. `node scripts/measure-graphify.mjs B 2`; same checks. Note whether the agent used the graph on the dependency questions (2-4) and not on the others.
- **Acceptance criteria:**
  - [ ] 10 runs recorded; graph use per question noted.

### [ ] T5 — Configuration C: graph first (~10 min)

- **Layer:** docs (measurement notes)
- **Files:** notes for T6
- **Depends on:** T2
- **What to do:**
  1. `node scripts/measure-graphify.mjs C 2`; same checks. This is the "widen its use" option the issue asks about.
- **Acceptance criteria:**
  - [ ] 10 runs recorded.

### [ ] T6 — ADR 0010: phase-2 measurement and conclusion (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0010-graphify-token-savings.md`
- **Depends on:** T3, T4, T5
- **What to do:**
  1. New section "Measurement on phase 2 (2026-11-05)": code size, Graphify version, method, a table question × A/B/C (both runs and the mean), cost in dollars if the JSON reports it, and how complete each answer was.
  2. Conclusion: keep the current rule, or propose widening it (C cheaper than B on general searches). The status line of the ADR says whether it was confirmed or a change is proposed; "To revisit in phase 2" in Consequences is marked as done.
- **Acceptance criteria:**
  - [ ] Every number in the table comes from the script output kept in T3-T5.

### [ ] T7 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/arquitectura-ia.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T6
- **What to do:**
  1. `arquitectura-ia.md`: the code-graph row mentions the phase-2 measurement and `scripts/measure-graphify.mjs`.
  2. Status: #71 done, next step `/plan-feature 72`; decision (kept or proposed change); `pendientes.md` loses the Graphify item (and gains the proposal for `.claude/`, if any); a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] Measurement done with the #57 method and results (tokens with and without the graph, by question type) in ADR 0010.
- [ ] Decision documented: the current use is kept or a wider use is proposed (with the user's approval, in its own issue).
- [ ] `./mvnw verify` green (the build does not change).

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no new tests).
- [ ] No dependencies added beyond the planned ones (Graphify is a per-machine development tool, not a build dependency, ADR 0010).
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Cost:** 30 headless runs with Haiku (3 configurations × 5 questions × 2 runs). In #57 one run cost about USD 0.004; expect well under USD 1, counted against your plan's usage.
- **Committing the script:** `scripts/measure-graphify.mjs` makes the next measurement a single command. If you would rather not add a script, it stays in the session's scratchpad and only the method goes into the ADR.
- **Environment differs from #57:** cloud Linux session instead of Windows, and possibly another Claude Code version. The A/B/C comparison is made in the same environment, so it stays valid; the absolute numbers are not compared with #57's.
- **Noise:** two runs per question reduce it but do not remove it; differences under ~15 % are reported as "no clear difference".
- **If C wins:** changing `buscador-referencias` and `plan-feature` touches `.claude/` and needs your explicit approval; it would be a new issue, not part of this PR.

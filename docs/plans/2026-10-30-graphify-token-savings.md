# Plan: Integrate Graphify to reduce token usage

- **Issue:** [#57](https://github.com/dawnrock/Proyect-DDD-Claude/issues/57)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-30
- **Status:** done
- **Total estimate:** ~95 min (11 tasks)

## Goal

Reduce the tokens Claude Code spends exploring the codebase, mainly for phase 2, where the move to hexagonal touches every package. [Graphify](https://pypi.org/project/graphifyy/) builds a knowledge graph of the Java code locally (tree-sitter, no API calls), and the agents query it (`graphify query`, `path`, `explain`) instead of re-reading files and sweeping them with Grep. The saving is measured before and after; if there is no noticeable saving, the ADR says so and the integration is reconsidered. It is done before #13, so the `v1-monolito` snapshot already includes it.

Nothing in the application or the Maven build changes.

### Out of scope

- Running Graphify in CI.
- Indexing `docs/` (it goes through the model and costs tokens). The glossary is only added if the measurement justifies it.
- The `Hexagonal` graph and the visual comparison of phases (#23).
- Running `graphify claude install` on the repository: it rewrites `CLAUDE.md`. Its effect is reproduced by hand instead.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build | `.gitignore`, `.graphifyignore` (new), `.graphifyrc` (new, if needed) | Ignore `graphify-out/`; code-only graph over `src/`, excluding `target/` and `.claude/worktrees/` |
| claude | `.claude/agents/buscador-referencias.md` | Query the graph first; Grep/Glob only for what the graph cannot answer. Adds `Bash(graphify query:*)`, `Bash(graphify path:*)`, `Bash(graphify explain:*)` to `tools` |
| claude | `.claude/skills/plan-feature/SKILL.md` | Step 3: read `graphify-out/GRAPH_REPORT.md` and query the graph before delegating wide searches |
| claude | `.claude/skills/implement-plan/SKILL.md` | Run `graphify update .` after creating the worktree (each worktree has its own `graphify-out/`) |
| claude | `.claude/settings.json` | Graphify `PreToolUse` hook on Glob/Grep next to `block-backlog-json.mjs`; `allow` for the read-only `graphify` commands |
| docs | `docs/adr/0010-graphify-token-savings.md` (new), `docs/adr/README.md` | Decision, alternatives, measurement |
| docs | `CLAUDE.md`, `docs/ia/arquitectura-ia.md`, `docs/ia/estado-del-proyecto.md` | How to use and regenerate the graph; new order (#57 before #13) |

Technical decisions:

- **Installation:** `py -m pip install --user graphifyy` (the machine has Python 3.14 through the `py` launcher; `uv` and `pipx` are not installed). The CLI is `graphify`; the PyPI package is `graphifyy`.
- **Code only:** `--code-only` and/or `GRAPHIFY_BACKEND=none`, so building the graph never costs tokens.
- **Changes to `CLAUDE.md` and `.claude/` need the user's explicit approval** (working agreement). Every task in the `claude` layer stops for it.
- The agent cannot edit `.claude/hooks/**` (`deny`). If Graphify's hook needs a script, the user copies it there.

## Tasks

### [x] T1 — Spike: install Graphify and inspect what it writes (~10 min)

- **Layer:** build
- **Files:** none in the repository (notes go to the ADR draft in T9)
- **Depends on:** —
- **What to do:**
  1. Ask the user to confirm installing the package (download from PyPI), then `py -m pip install --user graphifyy` and check `graphify --version`.
  2. Build a code-only graph of the worktree with `graphify update . --code-only` (or `graphify extract`) and check that it runs on Python 3.14.
  3. In a scratch copy outside the repository, run `graphify claude install` and write down exactly what it adds to `CLAUDE.md` and `.claude/settings.json` (event, matcher, command, any script).
  4. Note whether `graphify hook install` (git hooks) works with worktrees, since `.git/hooks` is shared and `graphify-out/` is per worktree.
- **Acceptance criteria:**
  - [x] `graphify --version` responds and `graphify-out/graph.json` exists.
  - [x] The content of the always-on hook and the `CLAUDE.md` section is recorded.

### [x] T2 — Exclusions and ignored output (~5 min)

- **Layer:** build
- **Files:** `.gitignore`, `.graphifyignore`, `.graphifyrc` (only if needed for `code-only`)
- **Depends on:** T1
- **What to do:**
  1. Add `graphify-out/` to `.gitignore`.
  2. Create `.graphifyignore` excluding `target/`, `.claude/worktrees/`, `docs/`, `scripts/` and `graphify-out/`; set code-only mode in `.graphifyrc` if Graphify supports it there.
  3. Rebuild the graph.
- **Acceptance criteria:**
  - [x] `git status` does not show `graphify-out/`.
  - [x] `graph.json` has no node under `target/`, `.claude/worktrees/` or `docs/`.
  - [x] Rebuilding makes no LLM call (no backend configured or `GRAPHIFY_BACKEND=none`).

### [x] T3 — Baseline measurement without the graph (~10 min)

- **Layer:** docs
- **Files:** notes for the ADR (T9)
- **Depends on:** T2
- **What to do:**
  1. Run `buscador-referencias` **as it is today** on three fixed questions:
     - Where is `CheckingAccount.withdraw` used?
     - What depends on `MoneyTransferService`?
     - Which `presentation` classes use `CheckingAccountService`?
  2. Write down the tokens of each run (the `Agent` result reports them) and whether the answer was complete.
- **Acceptance criteria:**
  - [x] Three baseline figures recorded, with the exact questions.

### [x] T4 — `buscador-referencias` queries the graph first (~10 min)

- **Layer:** claude
- **Files:** `.claude/agents/buscador-referencias.md`
- **Depends on:** T3
- **What to do:**
  1. Add `Bash(graphify query:*)`, `Bash(graphify path:*)` and `Bash(graphify explain:*)` to `tools`.
  2. Instructions: if `graphify-out/graph.json` exists, answer with the graph first; use Grep/Glob only for what it does not cover (`docs/`, `.claude/`, `pom.xml`, text that is not a symbol) or when `graphify` exits with code 3 or 4. Keep the output format.
  3. Ask for the user's approval before saving.
- **Acceptance criteria:**
  - [x] With the graph present, the agent answers the T3 questions using `graphify` and the same output format.
  - [x] Without `graphify-out/`, it still works with Grep/Glob.

### [x] T5 — `plan-feature` uses the graph (~5 min)

- **Layer:** claude
- **Files:** `.claude/skills/plan-feature/SKILL.md`
- **Depends on:** T4
- **What to do:**
  1. In step 3, before delegating wide searches: read `graphify-out/GRAPH_REPORT.md` if it exists and use `graphify query`/`path` for dependencies. Add those commands to `allowed-tools`.
  2. Ask for the user's approval before saving.
- **Acceptance criteria:**
  - [x] `allowed-tools` and step 3 mention the graph; the skill still works without it.

### [x] T6 — Always-on hook and permissions (~10 min)

- **Layer:** claude
- **Files:** `.claude/settings.json`
- **Depends on:** T1, T4
- **What to do:**
  1. Reproduce by hand the `PreToolUse` hook recorded in T1, as a second entry next to `block-backlog-json.mjs` (which keeps blocking `issues-fase-*.json`).
  2. Add to `allow`: `Bash(graphify query *)`, `Bash(graphify path *)`, `Bash(graphify explain *)`, `Bash(graphify update *)`.
  3. Ask for the user's approval before saving. If the hook needs a script under `.claude/hooks/`, the user copies it there.
- **Acceptance criteria:**
  - [x] A Grep in a session with the graph shows the Graphify hook acting, and reading `scripts/issues-fase-1.json` is still blocked.
  - [x] `graphify query` runs without a permission prompt.

### [x] T7 — Keep the graph fresh in each worktree (~5 min)

- **Layer:** claude
- **Files:** `.claude/skills/implement-plan/SKILL.md` (and `implement-plan-team` if it creates worktrees the same way)
- **Depends on:** T6
- **What to do:**
  1. After creating the worktree, run `graphify update .` (local, no tokens). Use `graphify hook install` instead only if T1 showed it works with worktrees.
  2. Ask for the user's approval before saving.
- **Acceptance criteria:**
  - [x] A new worktree created by the skill has `graphify-out/graph.json`.

### [x] T8 — Measurement with the graph (~10 min)

- **Layer:** docs
- **Files:** notes for the ADR (T9)
- **Depends on:** T4, T6
- **What to do:**
  1. Repeat the three T3 questions with the integration in place, in a new session.
  2. Compare tokens and completeness of the answers. Decide whether to add `docs/dominio/glosario.md` to the graph (only if the saving justifies its cost).
- **Acceptance criteria:**
  - [x] Before/after table with the three questions.

### [x] T9 — ADR 0010 (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0010-graphify-token-savings.md`, `docs/adr/README.md`
- **Depends on:** T8
- **What to do:**
  1. Context, decision (development tool, code only, manual integration), alternatives (`graphify claude install`, no graph, MCP server), consequences, and the measurement from T3/T8. If there is no noticeable saving, say so and propose removing the integration.
  2. Add the row to the ADR index.
- **Acceptance criteria:**
  - [x] ADR in English, linked in the index, with the measurement.

### [x] T10 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `CLAUDE.md`, `docs/ia/arquitectura-ia.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T9
- **What to do:**
  1. `CLAUDE.md`: in "Comandos", `graphify update .` (regenerate the graph); in the documentation index, the ADR. Ask for the user's approval.
  2. `arquitectura-ia.md`: row for Graphify in the parts map (hook, agent, skills).
  3. `estado-del-proyecto.md`: #57 done, next step #13, pending items from the spike and the measurement, history row.
- **Acceptance criteria:**
  - [x] `CLAUDE.md` has no section written automatically by Graphify.
  - [x] `node scripts/check-project-status.mjs` (CI check) passes.

### [x] T11 — Short project status (~10 min) · added during implementation

- **Layer:** docs · claude
- **Files:** `docs/ia/estado-del-proyecto.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md` (new), `implement-plan`, `plan-feature` (and its template), `revisor-ddd`, `scripts/check-project-status.mjs`, `CLAUDE.md`
- **Depends on:** T8
- **What to do:**
  1. Keep in `estado-del-proyecto.md` only what every session needs (where we are, next step, working agreements) and move decisions, pending items and history to annexes read on demand.
  2. Align the skills, the plan template, `revisor-ddd`, the CI message and `CLAUDE.md` with the new layout.
- **Acceptance criteria:**
  - [x] `estado-del-proyecto.md` goes from 13.3 KB to 2.9 KB and links its annexes.
  - [x] The CI check still passes for a PR that closes an issue.

## Deviations

- **T8 measured in new sessions.** Subagent definitions and hooks are loaded when a session starts, so a measurement inside the working session never used the graph. T3 and T8 were repeated with `claude -p --agent` (the pre-#57 agent from git and no graph vs. the new agent with graph). Result: 112,755 → 149,604 tokens; only the dependency question saved (−27 %). Details in ADR 0010.
- **Scope reduced after the measurement (decided by the user):** no `hook-guard` hook (T6 was reverted), and `buscador-referencias` and `plan-feature` use the graph only for dependency questions (`affected`, `path`). The glossary is not added to the graph.
- **`scripts/graphify.mjs`** (not in the plan): portable launcher, because `graphify` is not on the PATH (`pip --user` on Windows) and is not installed in CI.
- **T11 added (asked by the user):** the real per-session saving is the size of `estado-del-proyecto.md`, not code search.
- Implemented on the session branch renamed to `feat/graphify-token-savings` in the existing worktree, instead of a new worktree.

## Global acceptance criteria

**Specific:**

- [x] `graphify-out/` is generated only from the code, is not committed and costs no tokens to build.
- [x] `buscador-referencias` and `plan-feature` query the graph for dependency questions, and still work without it.
- [x] ~~The hook coexists with `block-backlog-json.mjs`.~~ n/a: the hook was removed after the measurement.
- [x] Before/after measurement documented in ADR 0010.
- [x] Every change to `CLAUDE.md` and `.claude/` was approved by the user.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (layers: each layer only uses the one below), verified by ArchUnit. *(Not affected: no Java changes.)*
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md`. *(n/a: no Java tests.)*
- [x] No dependencies added beyond the planned ones (or justified in an ADR). *(Graphify is a development tool, justified in ADR 0010; the `pom.xml` does not change.)*
- [x] If the API changes: `docs/api/openapi.yaml` reflects it. *(n/a)*
- [x] Any new business term is in `docs/dominio/glosario.md`. *(n/a)*
- [x] Documentation written or changed by this plan is in English.
- [x] `docs/ia/estado-del-proyecto.md` updated (and split into annexes, T11).

## Risks and open questions

- **Python 3.14:** Graphify asks for 3.10+, but the tree-sitter wheels may not exist yet for 3.14. If T1 fails, install Python 3.12/3.13 with the `py` launcher (it does not affect the project).
- **Exact hook content:** the public docs do not show what `graphify claude install` writes; T1 finds out in a scratch copy. If the hook turns out to be too intrusive (for example, it blocks Grep), T6 keeps only the agent and skill integration.
- **Graph per worktree:** each worktree needs its own `graphify-out/`; without T7, sessions in new worktrees would fall back to Grep and save nothing.
- **Small repository** (89 Java files, ~7,500 lines): the saving may be modest now; the bet is on phase 2. The measurement in T3/T8 decides.
- **Untrusted package:** Graphify is third-party code that runs on the machine and in hooks. Install only from PyPI (`graphifyy`), pin the version in the ADR.

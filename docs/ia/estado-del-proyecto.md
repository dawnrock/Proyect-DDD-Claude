# Project status

> **Read it at the start of every session.** It is the entry point to carry on without depending on earlier conversations, and it is kept **short on purpose**: it is loaded in every session. **It is updated in every PR that closes an issue** (CI checks it).

- **Last updated:** 2026-11-05 · PR #80 (#77, review leftovers)
- **Active branch:** `Hexagonal`, the repository's default branch; phase 2 is marked by the `v2-hexagonal` tag and work continues on the branch. `Monolithic` frozen at the `v1-monolito` tag; `master` frozen.

## Where we are

| Phase | Done | In progress | Pending |
| ----- | ---- | ----------- | ------- |
| 1 · Layered monolith | Closed: #2-#8, #11-#13, #31, #48, #50, #54, #57 (details in the [history](historial.md)) | — | — |
| 2 · Hexagonal | Closed: #14-#23, tag `v2-hexagonal` | — | — |
| After phase 2 (no milestone) | #70 translation, #71 Graphify, #72 web, #77 review leftovers | — | #78 transfer atomicity, #79 Graphify for methods |

## Next step

`/plan-feature 78` on `Hexagonal` (save both accounts of a transfer atomically); then #79 (Grep instead of the graph for method questions; touches `.claude/`, needs the user's approval).

Phase 2 design: `docs/arquitectura/fase-2-hexagonal.md`; comparison with phase 1: `docs/arquitectura/comparativa-capas-hexagonal.md`.

## Working agreements with the user

- **Always plan before implementing** (`/plan-feature`) and the user approves the plan.
- **The user decides the merges.** No PR is merged without their "yes". **Once merged, all its branches and worktrees are deleted** (local and remote, also the team's `-back`/`-web` ones), after checking they are contained in the base branch (2026-11-03).
- **`CLAUDE.md` and the `.claude/` configuration** only change with their explicit approval, never at an agent's request.
- **Diagrams are shown to the user before they are committed.** Diagram style: grayscale, no colours.
- The user may follow the session from the phone (Remote Control). **Telegram notices are on** (the user turned them back on on 2026-10-30): the skills run `scripts/notify.mjs` when they say so.
- Language: conversation, commits, `@DisplayName` and domain error messages in Spanish; code and **documentation in English** (since the language-convention PR, 2026-10-23; the older documents were translated in #70). Only `docs/analysis/` and the five phase-1 plans of 2026-10-23 stay in Spanish; file names are not translated.
- **Token savings (#57):** this file only holds what is needed to start. Everything else goes to its annexes and is only read when the task needs it.

## Annexes (read them only when the task needs it)

| Annex | When to read it | What each PR updates |
| ----- | --------------- | -------------------- |
| [`decisiones.md`](decisiones.md) | When planning, implementing or reviewing something in the area of a decision (errors, validation, transfers, security…) and for questions about the environment | New decisions that affect future issues |
| [`pendientes.md`](pendientes.md) | When planning an issue (there may be notes for it) | New pending items and the ones resolved |
| [`historial.md`](historial.md) | Only when you need to know when or in which PR something was done | One row per PR |

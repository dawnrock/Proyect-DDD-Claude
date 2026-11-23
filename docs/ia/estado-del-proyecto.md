# Project status

> **Read it at the start of every session.** It is the entry point to carry on without depending on earlier conversations, and it is kept **short on purpose**: it is loaded in every session. **It is updated in every PR that closes an issue** (CI checks it).

- **Last updated:** 2026-11-24 · PR #163 (#156, PostgreSQL connection timeouts in `prod`)
- **Active branch:** `Hexagonal`, the repository's default branch; phase 2 is marked by the `v2-hexagonal` tag and work continues on the branch. `Monolithic` frozen at the `v1-monolito` tag (exceptions authorised by the user: the Render deployment, #86, the footer, #91, and the twins #99 and #95); `master` frozen.

## Where we are

| Phase | Done | In progress | Pending |
| ----- | ---- | ----------- | ------- |
| 1 · Layered monolith | Closed: #2-#8, #11-#13, #31, #48, #50, #54, #57 (details in the [history](historial.md)) | — | — |
| 2 · Hexagonal | Closed: #14-#23, tag `v2-hexagonal` | — | — |
| After phase 2 (no milestone) | #70 translation, #71 Graphify, #72 web, #77 review leftovers, #78 unit of work, #79 Claude Code configuration, #82 code leftovers, #85 and #86 deploy both branches on Render, #89 and #91 web footer, #98 segment by verified income and by balance (Mon-A Bank IBAN, entity `9999`), #103 customer sign-up (twin of #95; on `Monolithic`: #99 in PR #101, #95 in PR #102), #108 contract discrepancies (PR #114), #113 no multipart (PR #115), #107 contract tests (PR #117), #109 OpenAPI style guide (PR #118), #110 contract examples (PR #119), #111 Swagger UI (PR #120), #112 contract versioning (PR #121), #122 JWT Bearer on the API (PR #123, ADR 0015, contract 2.0.0), #124 web frontend base (PR #127, ADR 0016), #125 htmx in the account operations (PR #128), #126 htmx navigation for the rest of the web (PR #129), #130 limit on login attempts (PR #132, ADR 0017), #133 client IP behind Cloudflare (PR #134; checked on Render by the user), #131 limit on customer sign-ups (PR #135, ADR 0018), #136 implementation skills on the branch imposed by cloud sessions (PR #137), #139 limit on sign-up probing (PR #144), #145 accessible validation errors and focus on the first error (PR #149), #146 required fields, mobile keyboard and show password (PR #151), #147 closed (password repetition kept), #152 username format explained and lower-cased on the web (PR #153), #150 real persistence: JPA over PostgreSQL (Neon) in production and H2 locally, users in the database (PR #154, ADR 0019), #157 case-insensitive username on login, a regression of #150 (PR #158), #156 PostgreSQL connection timeouts in `prod` (PR #163) | — | — |

## Next step

1. #159: the contract version rule must read `info.version` with CRLF line endings (Windows). Then: the event outbox (#17 leftovers in `pendientes.md`, issue to open) and, after it, OAuth / Auth0. The user still has to check a Render deploy with the plain Neon URL (#156, `pendientes.md`).

Both branches are live on Render (guide `docs/deploy/render.md`).

Phase 2 design: `docs/arquitectura/fase-2-hexagonal.md`; comparison with phase 1: `docs/arquitectura/comparativa-capas-hexagonal.md`.

## Working agreements with the user

- **Always plan before implementing** (`/plan-feature`) and the user approves the plan, **except with `/advance-issues`** (2026-11-17): Claude goes through the open issues in the most coherent order without waiting for approval between steps, and only asks (`AskUserQuestion` with options) when a decision of the user's is needed; `CLAUDE.md`/`.claude/`, new dependencies, `Monolithic` and diagrams still need the user.
- **Merges:** a PR the user asked for (with `/implement-plan`, `/implement-plan-team`, `/advance-issues` or by asking for a PR) is merged by Claude as soon as all its checks are green, with no blocking review finding and no conflict, never against `master` (step 7 bis of `implement-plan`, 2026-11-17). Any other PR is not merged without the user's "yes". **Once merged, all its branches and worktrees are deleted** (local and remote, also the team's `-back`/`-web` ones), after checking they are contained in the base branch (2026-11-03).
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

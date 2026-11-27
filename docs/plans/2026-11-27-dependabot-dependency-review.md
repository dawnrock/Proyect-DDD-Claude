# Plan: Dependabot and dependency review on pull requests

- **Issue:** [#204](https://github.com/dawnrock/Proyect-DDD-Claude/issues/204)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-27
- **Status:** in progress
- **Total estimate:** ~30 min (4 tasks)

## Goal

Close the largest OWASP Top 10 gap of the project, **A06 · Vulnerable and outdated components**: today nothing warns about a CVE in Spring Boot, the WebJars, the Docker base images or the GitHub Actions. Dependabot opens weekly update pull requests against `Hexagonal`, and a dependency review job fails any pull request that adds a dependency with a known high or critical vulnerability. No dependency is added to `pom.xml`.

### Out of scope

- OWASP Dependency-Check (needs an NVD API key, slows the build, many false positives).
- `Monolithic` and `master` (frozen).
- Assets Dependabot cannot see: Motion copied into `static/vendor/motion`, Swagger UI from jsDelivr with SRI, and the tools pinned in `ci.yml` (Redocly, oasdiff, Schemathesis). They go to `pendientes.md`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build | `.github/dependabot.yml` (new) | Maven, GitHub Actions and Docker, weekly, against `Hexagonal` |
| build | `.github/workflows/ci.yml` | New `dependency-review` job on pull requests |
| build | `.github/workflows/claude-code-review.yml` | Skip Dependabot pull requests: repository secrets are not passed to their runs, so the review would always fail |
| docs | `docs/ia/*.md` | Who merges Dependabot pull requests, what it does not cover |

- The repository is public, so the dependency graph and `actions/dependency-review-action` work without GitHub Advanced Security.
- The `verify` job already tolerates missing secrets: `scripts/notify.mjs` reports "not configured" instead of failing, and `check-project-status.mjs` only applies to pull requests that close an issue.
- Existing agreement: a pull request the user did not ask for is not merged without the user's "yes". Dependabot pull requests fall under it.

## Tasks

### [x] T1 — Dependabot configuration (~10 min)

- **Layer:** build
- **Files:** `.github/dependabot.yml`
- **Depends on:** —
- **What to do:**
  1. `version: 2` with three `updates` entries, all with `directory: "/"`, `target-branch: "Hexagonal"`, `schedule.interval: weekly` (Monday) and `open-pull-requests-limit: 5`:
     - `maven`: one group for `minor` and `patch` updates; majors stay as separate pull requests. `commit-message.prefix: "build"`.
     - `github-actions`: one group for everything. `commit-message.prefix: "ci"`.
     - `docker`: `commit-message.prefix: "build"`; `ignore` major updates (JDK changes such as `eclipse-temurin:21` → `25` are a project decision, not a routine bump).
  2. Comment at the top (in Spanish, like the other workflow files) on why and which ADR/issue.
- **Acceptance criteria:**
  - [x] The file parses as YAML (`python3 -c "import yaml,sys; yaml.safe_load(open('.github/dependabot.yml'))"`) and uses only keys from the Dependabot v2 schema.
  - [ ] After the merge, the repository's Insights → Dependency graph → Dependabot tab shows the three ecosystems with no configuration error (owner-observable; noted in the PR). _Pending: only observable after the merge._

### [x] T2 — Dependency review job (~5 min)

- **Layer:** build
- **Files:** `.github/workflows/ci.yml`
- **Depends on:** —
- **What to do:**
  1. New job `dependency-review` with `if: github.event_name == 'pull_request'`, `permissions: contents: read`, `actions/checkout@v4` and `actions/dependency-review-action@v4` with `fail-on-severity: high`.
  2. Keep the `verify` job unchanged; the Telegram notice stays in `verify`.
- **Acceptance criteria:**
  - [ ] The PR of this issue shows the `dependency-review` check in green.

### [ ] T3 — Code review skips Dependabot (~5 min)

- **Layer:** build
- **Files:** `.github/workflows/claude-code-review.yml`
- **Depends on:** —
- **What to do:**
  1. `if: github.actor != 'dependabot[bot]'` on the `claude-review` job (Dependabot runs do not get `CLAUDE_CODE_OAUTH_TOKEN`), with a comment saying why.
- **Acceptance criteria:**
  - [ ] The workflow still runs on this PR (the actor is not Dependabot).
  - [ ] On the first Dependabot PR the job shows as skipped, not failed (checked once after the merge).

### [ ] T4 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`
- **Depends on:** T1, T2, T3
- **What to do:**
  1. `decisiones.md`: Dependabot (ecosystems, grouping, majors apart, Docker majors ignored) and that its pull requests are merged only with the user's "yes"; Claude can review them on request (`/code-review`).
  2. `pendientes.md`: what Dependabot does not cover (Motion, Swagger UI with its SRI hashes, tools pinned in `ci.yml`), and the manual step for the owner: turn on **Dependabot alerts** and **security updates** in the repository settings (they are not part of `dependabot.yml`).
  3. Project status: #204 done, next step #205; one row in `historial.md`.
- **Acceptance criteria:**
  - [ ] `node scripts/check-project-status.mjs` passes in CI (the PR closes #204 and touches the status file).

## Global acceptance criteria

**Specific:**

- [ ] `.github/dependabot.yml` covers Maven, GitHub Actions and Docker against `Hexagonal`.
- [ ] The `dependency-review` job runs on pull requests and is green on this one.
- [ ] Dependabot pull requests do not fail because of missing secrets.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (dependencies point to the hexagon), verified by ArchUnit. (No Java changes.)
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` — n/a (no Java tests).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR) — none added.
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md` — n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- Dependabot can only be checked end to end after the merge (its first run). If the configuration has an error, GitHub shows it in the Dependabot tab and in a failed "Dependabot updates" run; the fix is a follow-up commit.
- A Spring Boot parent bump can change managed versions (Jackson, Hibernate, Flyway) and break the build; the CI shows it and the pull request stays open for the user.
- WebJar bumps (Bootstrap, htmx, Lucide) change the frontend; they go in the grouped minor/patch pull request and the user decides.

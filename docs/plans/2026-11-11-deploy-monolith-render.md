# Plan: Deploy the layered monolith on Render

- **Issue:** [#86](https://github.com/dawnrock/Proyect-DDD-Claude/issues/86)
- **Type:** feature (build and deployment; no application code changes)
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-11-11
- **Status:** in progress
- **Total estimate:** ~30 min (4 tasks)

## Goal

Make `Monolithic` deployable on Render's free plan as its own web service, with the same approach as #85 (Docker, `demo` profile, port and passwords from the environment).

`Monolithic` is frozen at `v1-monolito`. **Exception explicitly authorised by the user on 2026-11-11:** one PR against `Monolithic` that only adds what the deployment needs (`Dockerfile`, `.dockerignore`, a guide, a line in the `README.md` and the project status the CI requires). No file under `src/` changes and the `v1-monolito` tag does not move.

### Out of scope

- Any change to the phase-1 code or tests.
- Creating the Render service (the user does it).

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| build | `Dockerfile`, `.dockerignore` | Same as in #85 (the build is identical: Maven, Java 21, Spring Boot 4.1) |
| docs | `docs/deploy/render.md`, `README.md` | Guide pointing at branch `Monolithic`; "Deploy" section |
| docs | `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md` | Short note of the exception (CI requires the status on a PR that closes an issue) |

This plan is implemented **after #85 is merged**, from a branch based on `origin/Monolithic`, so the files are copied from the version already reviewed on `Hexagonal`. The phase-1 `demo` profile has the same users and the same `app.security.users[*]` properties, so the password override works the same way (checked in T2).

## Tasks

### [x] T1 — `Dockerfile` and `.dockerignore` (~10 min)

- **Layer:** build
- **Files:** `Dockerfile`, `.dockerignore`
- **Depends on:** #85 merged
- **What to do:**
  1. Copy both files from `Hexagonal` (no differences expected); `./mvnw verify` on `Monolithic`.
- **Acceptance criteria:**
  - [x] The image (or, without a Docker daemon, the same `java` command line) answers `UP` on `/actuator/health` with `PORT` set.
  - Files copied unchanged from `Hexagonal` (#87). Built with Docker as in #85 (the session's build adds only its proxy CA, out of the repository); `docker run --memory=512m -e PORT=10000`: `UP` on port 10000 after ~13 s, profile `demo`, user `app` (uid 10001), ~250 MB of 512 MB.

### [x] T2 — Password override and guide (~10 min)

- **Layer:** docs
- **Files:** `docs/deploy/render.md`, `README.md`
- **Depends on:** T1
- **What to do:**
  1. Check the password override as in #85 T2 (old password 401, new one 200).
  2. Guide adapted to branch `Monolithic` (otherwise the same as #85's); "Deploy" section in the `README.md`.
- **Acceptance criteria:**
  - [x] Override checked; links resolve.
  - As found in #85, each user is given complete (`APP_SECURITY_USERS_<n>_USERNAME`, `_PASSWORDHASH`, `_ROLE`). With the twelve variables: `admin:demo` and `grace:demo` → 401, the new password → 200 for `admin`, `grace` and `alan`; `grace` sees only her account; web login works; `/h2-console/` → 404 after an admin login; `APP_ADMIN_*` adds an administrator. The guide differs from #85's only in the branch, the service and image names, and the demo data (phase 1 only opens the three accounts).

### [x] T3 — No code changes (~2 min)

- **Layer:** build
- **Files:** —
- **Depends on:** T2
- **What to do:**
  1. `git diff --name-only origin/Monolithic...HEAD` lists no file under `src/` nor `pom.xml`.
- **Acceptance criteria:**
  - [x] Checked: only `Dockerfile`, `.dockerignore`, `README.md`, `docs/deploy/render.md` and the plan (plus the status in T4); `v1-monolito` still points at `origin/Monolithic` (10eb227).

### [ ] T4 — Project status (~8 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md` (on `Monolithic`)
- **Depends on:** T3
- **What to do:**
  1. On `Monolithic`: the status says the branch stays frozen at `v1-monolito` except for this deployment PR (#86); decision and history row. On `Hexagonal` the status is updated when #86 is merged (in the next PR there), as #61 did for #13.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes on the PR against `Monolithic`.

## Global acceptance criteria

**Specific:**

- [ ] The image builds and starts; `/actuator/health` answers `UP`.
- [ ] No file under `src/` changes; `./mvnw verify` green on `Monolithic`.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers), verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a.
- [ ] No dependencies added beyond the planned ones (`pom.xml` unchanged).
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated (on `Monolithic`), with the exception recorded.

## Risks and open questions

- **The frozen branch moves:** only by the files listed above, with the user's authorisation; the tag keeps the original snapshot, and the comparison of #23 keeps using the tags.
- **Phase-1 documentation language:** the `Monolithic` annexes are in Spanish (translated only on `Hexagonal` in #70); the few lines added there follow the language of each file.

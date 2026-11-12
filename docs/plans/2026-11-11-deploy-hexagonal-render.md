# Plan: Deploy the hexagonal version on Render

- **Issue:** [#85](https://github.com/dawnrock/Proyect-DDD-Claude/issues/85)
- **Type:** feature (build and deployment; no application code changes)
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-11
- **Status:** in progress
- **Total estimate:** ~45 min (5 tasks)

## Goal

Make `Hexagonal` deployable on **Render's free plan** as its own web service, separate from the phase-1 one (#86). Decided by the user on 2026-11-11: Render (no card needed) rather than Google Cloud Run, and a password of their own for the demo users, because a public URL with the README's `demo` password would let anyone in.

Render only runs Java through **Docker**, so the repository gains a `Dockerfile`. The application code does not change: the `demo` profile, the port and the passwords are set from outside (environment variables), all of which Spring Boot already supports.

### Out of scope

- Creating the Render account and services, and connecting GitHub: the user does it (it needs their credentials).
- Real persistence: data stays in memory and is rebuilt by the `demo` profile on every start (Render's free plan puts the service to sleep when idle).
- CI building the image, or any automatic deployment besides Render's own "deploy on push".

## Context

| Area | File | Change |
| ---- | ---- | ------ |
| build | `Dockerfile` (new), `.dockerignore` (new) | Multi-stage: build the JAR with Maven + JDK 21 (tests skipped: CI already runs `verify`), run it on a JRE 21 image as a non-root user |
| docs | `docs/deploy/render.md` (new), `README.md` | Step-by-step guide and a "Deploy" section |
| docs | `docs/ia/*` | Status |

Facts gathered:

- `java -jar` does **not** load the `demo` profile (only `spring-boot:run` does, through `pom.xml`): the image sets `SPRING_PROFILES_ACTIVE=demo`.
- `application.properties` fixes `server.port=8080`; Render gives the port in `PORT`. The container passes `--server.port=${PORT:-8080}`.
- Render's free instance has 512 MB: the JVM gets `-XX:MaxRAMPercentage=75`.
- The demo users are `app.security.users[0..3]` with a BCrypt hash in `application-demo.properties`; Spring's relaxed binding lets an environment variable such as `APP_SECURITY_USERS_0_PASSWORDHASH` replace each hash without touching the code. `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` (existing) add a real administrator.
- Health check for Render: `GET /actuator/health` (public).
- The Docker daemon is not running in the cloud session; if it cannot be started, the image's command line is verified by running the same JAR with the same variables, and the build is left to Render (stated in the PR).

## Tasks

### [x] T1 — `Dockerfile` and `.dockerignore` (~10 min)

- **Layer:** build
- **Files:** `Dockerfile`, `.dockerignore`
- **Depends on:** —
- **What to do:**
  1. Build stage: `maven` image with JDK 21, copy `pom.xml` first (dependency cache), then `src/` and `lombok.config`; `mvn -q -DskipTests package`. Run stage: `eclipse-temurin:21-jre`, non-root user, `SPRING_PROFILES_ACTIVE=demo`, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`, `EXPOSE 8080`, start with the port from `PORT` (default 8080).
  2. `.dockerignore`: `target/`, `.git/`, `.claude/`, `graphify-out/`, `docs/`, IDE files.
- **Acceptance criteria:**
  - [x] `docker build` + `docker run -e PORT=10000` answer `UP` on `/actuator/health` at port 10000 — or, without a Docker daemon, the same `java` command line with the same variables does, and the build is left to Render.
  - Done with Docker: the daemon could be started in the session. The session's HTTPS proxy needs its own CA, so the local build used a copy of the `Dockerfile` (kept out of the repository) whose build stage only adds that CA and proxy to `MAVEN_OPTS`; the run stage is the committed one. `docker run --memory=512m -e PORT=10000`: `UP` on port 10000 after ~8 s, profile `demo`, user `app` (uid 10001), ~240 MB of 512 MB; image ~570 MB.

### [x] T2 — The demo users' password from the environment (~10 min)

- **Layer:** build (verification)
- **Files:** — (checked by running the JAR)
- **Depends on:** T1
- **What to do:**
  1. Generate a BCrypt hash for a test password (`htpasswd -nbBC 10 "" '<password>' | tr -d ':\n'` or another documented way) and start the app with `APP_SECURITY_USERS_0_PASSWORDHASH` … `_3_` set to it.
  2. Check: `curl -u admin:demo …/api/accounts` → 401, `curl -u admin:<password> …` → 200; the same for one customer.
- **Acceptance criteria:**
  - [x] The old password stops working and the new one works, with no code change.
  - **Deviation:** setting only the four `..._PASSWORDHASH` variables makes the application fail to start (`userDetailsService`: users without a name). Spring Boot replaces a whole list when a property source sets any element of it, so the environment's `app.security.users` list had only hashes. Each user must be given complete: `APP_SECURITY_USERS_<n>_USERNAME`, `_PASSWORDHASH` and `_ROLE` (12 variables), with the same names as the `demo` profile because the demo accounts belong to `admin`, `ada`, `grace` and `alan`. With them: `admin:demo` and `grace:demo` → 401; the new password → 200 for `admin`, `grace` and `alan`; `grace` still sees only her account. The hash was generated with `docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" '<password>' | tr -d ':\n'` (`$2y$`, accepted by Spring's BCrypt).

### [x] T3 — Deployment guide (~10 min)

- **Layer:** docs
- **Files:** `docs/deploy/render.md`
- **Depends on:** T2
- **What to do:**
  1. Steps: Render account → New Web Service → this repository, branch `Hexagonal`, runtime Docker, free instance, health check path `/actuator/health`, environment variables (the four password hashes and, optionally, `APP_ADMIN_*`), how to generate the hash, what to expect (sleep after idle, ~1 min cold start, data rebuilt on each start), how to redeploy (push) and how to stop (suspend or delete the service).
  2. A note that the phase-1 service is the same with branch `Monolithic` (#86).
- **Acceptance criteria:**
  - [x] Every command in the guide was run in T1-T2 (except the ones that happen on Render's site): the three ways to get the hash (`htpasswd` through the `httpd` image, and Python `bcrypt`), the twelve variables, `APP_ADMIN_*`, the health and `curl` checks, and the browser login with the new password.
  - **Addition:** with a public URL, the H2 console (admin only) would still be a way to run code on the server if the admin password leaked. The image sets `SPRING_H2_CONSOLE_ENABLED=false`; checked: `/h2-console/` → 404 after an admin login, the rest of the site unchanged. Local runs keep the console.

### [ ] T4 — `README.md` (~5 min)

- **Layer:** docs
- **Files:** `README.md`
- **Depends on:** T3
- **What to do:**
  1. "Deploy" section: one line on Render + Docker and a link to the guide; note that the demo password must be replaced on a public URL.
- **Acceptance criteria:**
  - [ ] Links resolve.

### [ ] T5 — Project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md`
- **Depends on:** T4
- **What to do:**
  1. Status: #85 done, next step #86 on `Monolithic`; decision (Render free plan, Docker, configuration only through environment variables, own password on a public URL); a row in `historial.md`.
- **Acceptance criteria:**
  - [ ] The CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] The image builds and starts with `PORT` and the `demo` profile; `/actuator/health` answers `UP` (or the equivalent check without Docker, stated).
- [ ] With the environment variables, the `demo` password stops working and the new one works.
- [ ] Guide and `README.md` updated.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected, verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no new tests).
- [ ] No dependencies added beyond the planned ones: Docker images are a deployment tool, not a build dependency (`pom.xml` unchanged).
- [ ] If the API changes: n/a.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Docker in the cloud session:** the daemon is not running; if it cannot be started, the image is first built by Render. Any error there is fixed in a follow-up commit.
- **Render's conditions change** (free hours, memory, sleep time): the guide says to check the current plan.
- **One blueprint for both services?** Render can read a `render.yaml` that declares both services (each with its branch). It would need to live in one branch and refer to the other; the plan keeps two manual services, simpler to follow. Say if you prefer the blueprint.

# Plan: Interactive API documentation with Swagger UI from the hand-written contract

- **Issue:** [#111](https://github.com/dawnrock/Proyect-DDD-Claude/issues/111)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** in progress
- **Total estimate:** ~45 min (5 tasks)

## Goal

Serve Swagger UI (OpenAPI course, chapter 10) at `/api-docs`, in local and on Render, built from the **hand-written** `docs/api/openapi.yaml` (the single source of truth, ADR 0007), so the API can be read and tried (`Try it out`, `Authorize` with HTTP Basic) without generating a second contract from the code.

Decided by the user (2026-11-17): **option A**, a static page that loads `swagger-ui-dist` from a CDN at a pinned version; no dependency in `pom.xml`.

### Out of scope

- A link from the web footer (the README links it; the footer stays as #89/#91 left it).
- Versioning the contract (#112).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build | `pom.xml` (`<build><resources>`, no dependency) | Copies `docs/api/openapi.yaml` to `static/openapi.yaml` in the jar, so the documentation can never drift from the contract |
| build | `Dockerfile`, `.dockerignore` | The image build also gets `docs/api/openapi.yaml` (today `docs/` is excluded, so Render would ship a jar without it) |
| `adapter.in.web` | `WebSecurityConfig.java` | `/openapi.yaml` and `/api-docs/**` are public; `Try it out` still needs HTTP Basic on `/api/**` (ADR 0008) |
| `adapter.in.web` | `ApiDocsController.java` (new) | `GET /api-docs` → `redirect:/api-docs/index.html` |
| resources | `static/api-docs/index.html` (new) | Swagger UI 5.34.1 from jsDelivr with Subresource Integrity, pointing to `/openapi.yaml` |
| docs | `docs/api/openapi.yaml` | First server `url: /` ("the server that serves this document"), so `Try it out` targets wherever the page is served (local or Render) |
| docs | `README.md`, `docs/deploy/render.md` | Where the documentation is |

Decisions taken while planning (clear and reversible, so not asked):

- **Version 5.34.1** of `swagger-ui-dist` (published 2026-10-30, more than two weeks old), with `integrity` hashes computed from the npm package, so a changed file on the CDN is refused by the browser.
- **Relative server first** in the contract (`/`): Swagger UI selects the first server by default, and `localhost:8080` would fail on Render. Schemathesis is unaffected (it gets `--url`).
- `/api-docs` is under the **web** chain (it does not match `/api/**`), so the page is served with the web chain's headers; the API calls it makes go to `/api/**` with Basic credentials, as any client.

## Tasks

### [x] T1 — The contract is served at `/openapi.yaml` (~10 min)

- **Layer:** build · adapter.in
- **Files:** `pom.xml`, `WebSecurityConfig.java`, `DddLabApplicationTests.java`
- **Depends on:** —
- **What to do:**
  1. Test first (full context, real server): an anonymous `GET /openapi.yaml` answers 200 and its body starts with `openapi: 3.1.0` (today: redirect to `/login`).
  2. `pom.xml`: a second `<resource>` (`docs/api`, include `openapi.yaml`, `targetPath` `static`) next to the default one; `WebSecurityConfig`: `permitAll` for `/openapi.yaml`.
- **Acceptance criteria:**
  - [x] Red before, green after; the YAML in the jar is the same file as `docs/api/openapi.yaml`.

### [x] T2 — Swagger UI page at `/api-docs` (~10 min)

- **Layer:** adapter.in
- **Files:** `ApiDocsController.java`, its `@WebMvcTest`, `static/api-docs/index.html`, `WebSecurityConfig.java`, `DddLabApplicationTests.java`, `docs/api/openapi.yaml`
- **Depends on:** T1
- **Deviation:** jsDelivr is not reachable from this session's network, so the browser check served the two CDN URLs from the files of the `swagger-ui-dist@5.34.1` npm package (the same bytes jsDelivr serves): SRI accepted, 8 operations and 3 servers listed, no console error, and `Try it out` on `GET /api/accounts` with `admin`/`demo` answered 200 through the relative server.
- **What to do:**
  1. Tests first: `GET /api-docs` redirects to `/api-docs/index.html` (`@WebMvcTest`); an anonymous `GET /api-docs/index.html` answers 200 with the Swagger UI bootstrap pointing to `/openapi.yaml` (full context).
  2. Controller, page (pinned version, SRI, `url: '/openapi.yaml'`, `persistAuthorization: false`), `permitAll` for `/api-docs/**`, and the relative server in the contract (Redocly clean).
- **Acceptance criteria:**
  - [x] Tests red before, green after; Redocly clean; the page loads in a browser (checked with Playwright: the operations of the contract are listed).

### [x] T3 — Docker image includes the contract (~10 min)

- **Layer:** build
- **Files:** `Dockerfile`, `.dockerignore`
- **Depends on:** T1
- **Deviation:** there is no Docker daemon in this session, so the image could not be built. The build context was emulated (`pom.xml`, `lombok.config`, `src/` and, with the change, `docs/api/openapi.yaml`) and built with Maven: without the file the jar has no `static/openapi.yaml` (Maven skips the missing resource directory silently), with it the jar does. The `.dockerignore` exception (`!docs/api/openapi.yaml` after `docs/`) is checked on the next Render deploy: `GET https://proyect-ddd-claude.onrender.com/openapi.yaml` must answer 200.
- **What to do:**
  1. `.dockerignore`: keep excluding `docs/` but allow `docs/api/openapi.yaml`; `Dockerfile`: `COPY docs/api/openapi.yaml ./docs/api/` before `mvn package`.
  2. Build the image and check `openapi.yaml` is inside the jar (if the registry cannot be reached from this session, check the jar of a Maven build from a copy of the build context instead and say so).
- **Acceptance criteria:**
  - [x] The image's jar contains `BOOT-INF/classes/static/openapi.yaml`.

### [ ] T4 — Documentation (~5 min)

- **Layer:** docs
- **Files:** `README.md`, `docs/deploy/render.md`
- **Depends on:** T2, T3
- **What to do:**
  1. README: `/api-docs` in local and on Render; deploy guide: the documentation is served by the image.
- **Acceptance criteria:**
  - [ ] Links and URLs correct.

### [ ] T5 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`
- **Depends on:** T4
- **What to do:**
  1. `decisiones.md`: Swagger UI from the hand-written contract, option A, version and SRI, relative server, Docker context. `pendientes.md`: the CDN needs internet in the browser.
- **Acceptance criteria:**
  - [ ] Annexes updated.

## Global acceptance criteria

**Specific:**

- [ ] `/api-docs` shows the hand-written contract, in local and in the Docker image, without any new dependency.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes (the API does not change; the contract gets a relative server: both are run).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Internet in the browser:** the page loads Swagger UI from jsDelivr; offline it shows nothing (option B, the WebJar, would avoid it, at the cost of a dependency).
- **Render:** the deployed service picks the change up on its next deploy; the first request after sleeping may take about a minute.

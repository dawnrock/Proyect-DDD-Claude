# Plan: Stop answering 500 to a multipart/form-data request without a boundary

- **Issue:** [#113](https://github.com/dawnrock/Proyect-DDD-Claude/issues/113)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** in progress
- **Total estimate:** ~15 min (2 tasks)

## Goal

Today any request with `Content-Type: multipart/form-data` and no `boundary` answers **500** with Spring Boot's default JSON, on every API operation and on the web (found by Schemathesis, check `ST102`, while verifying #108). Cause, from the stack trace: `DispatcherServlet.checkMultipart` asks Spring Boot's `StandardServletMultipartResolver` to parse the body **before** a handler is chosen; the `FileUploadException` becomes a `MultipartException` with no handler, so `RestExceptionHandler` (bound to `@RestController`) never sees it and nothing translates it.

Expected: a client error, never a 500. Nothing in the application accepts multipart (no `MultipartFile`, no `enctype="multipart/form-data"` in the templates), so the fix is to **turn multipart resolution off** (`spring.servlet.multipart.enabled=false`): the request then reaches the controller like any other, a `GET` answers normally (its `Content-Type` is irrelevant) and a `POST` with a JSON body answers **415** through the existing `HttpMediaTypeNotSupportedException` handling.

### Out of scope

- The contract-test job in the CI (#107), which this issue unblocks.
- The `title` of the 415 `ProblemDetail`: `RestExceptionHandler` only sets the Spanish title for 400, so 415 keeps Spring's English `Unsupported Media Type` (already true today for `application/xml`). Noted for #109/#110.
- Documenting 415 in `docs/api/openapi.yaml`: not a behaviour this issue introduces (any non-JSON body already got it).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `resources` (configuration) | `src/main/resources/application.properties` | `spring.servlet.multipart.enabled=false`, with a comment saying why |
| tests (root) | `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java` | Reproduction against the real server (`RANDOM_PORT`, `HttpClient`) |

Why the full-context test and not `@WebMvcTest`: MockMvc's `MockHttpServletRequest.getParts()` returns an empty list instead of failing, so the bug does not reproduce there; and `AdapterTestIsolationTests` forbids `@SpringBootTest` in `adapter.in..`, so the test lives in `DddLabApplicationTests`, the place for full-context tests (`src/test/CLAUDE.md`).

Alternative considered and discarded: translating `MultipartException` to 400 (a handler that also catches exceptions with no handler). More code, and it keeps parsing bodies nobody uses.

No change to the domain, the ports or the adapters' code; no new dependency; no ArchUnit change; no API contract change.

## Tasks

### [x] T1 — Reproduce the 500 and turn multipart resolution off (~10 min)

- **Layer:** config · tests
- **Files:** `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`, `src/main/resources/application.properties`
- **Depends on:** —
- **What to do:**
  1. Test first (`fix`: reproduce), in a `@Nested` class "Peticiones multipart sin delimitador (#113)" of `DddLabApplicationTests`, with the existing `HttpClient` and the test user `admin`:
     - `GET /api/accounts` with `Content-Type: multipart/form-data` answers 200 (today 500).
     - `POST /api/accounts/{id}/deposits` with that `Content-Type` and a JSON body answers 415 `application/problem+json` (today 500).
     - `GET /login` (web) with that `Content-Type` answers 200 (today 500).
  2. Run it and confirm it fails with 500 for the expected reason.
  3. Add `spring.servlet.multipart.enabled=false` to `application.properties` with a short Spanish comment (nothing in the application accepts multipart; with it on, a malformed multipart request failed with 500 before reaching any controller).
- **Acceptance criteria:**
  - [x] The three checks fail with 500 before the property and pass after it.
  - [x] `./mvnw verify` green (web forms, H2 console and every existing test unaffected).

### [ ] T2 — Verify with Schemathesis and update the project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`
- **Depends on:** T1
- **What to do:**
  1. Start the app with the `demo` profile and run Schemathesis 4.33.4 as in #108 (`schemathesis run docs/api/openapi.yaml --url http://localhost:8080 --auth admin:demo`).
  2. `decisiones.md`: multipart resolution is off; an endpoint that ever needs uploads must turn it back on and handle `MultipartException`.
  3. `pendientes.md`: remove the `ST102`/#113 item from the #107 note (keep the `ST202` and test-data items); add the English `title` of 415 for #109/#110.
- **Acceptance criteria:**
  - [ ] Schemathesis reports no `ST102`; anything left is only the `ST202` cases already accepted for #107.
  - [ ] `curl -i -u admin:demo -H 'Content-Type: multipart/form-data' localhost:8080/api/accounts` → 200.

## Global acceptance criteria

**Specific:**

- [ ] No request with a malformed multipart `Content-Type` answers 5xx, on the API or the web.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it and `npx @redocly/cli lint docs/api/openapi.yaml` has no errors (n/a: the contract does not change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Web POST with multipart:** on the web chain, a `POST` without a CSRF token is rejected with 403 before Spring MVC; with a token, the form controllers read `application/x-www-form-urlencoded` and a multipart body would arrive without its fields (validation errors, not a 500). Covered by T1's `GET /login` check plus the existing web tests.
- **Future uploads:** if a feature ever needs file uploads, it must turn multipart back on for that endpoint and translate `MultipartException` (recorded in `decisiones.md`).

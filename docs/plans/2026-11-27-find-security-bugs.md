# Plan: Find Security Bugs in the static analysis

- **Issue:** [#205](https://github.com/dawnrock/Proyect-DDD-Claude/issues/205)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-27
- **Status:** done
- **Total estimate:** ~25 min (3 tasks)

## Goal

Add **Find Security Bugs** (`com.h3xstream.findsecbugs:findsecbugs-plugin` 1.14.0) to the existing SpotBugs run of `./mvnw verify` (ADR 0020). It adds about 140 OWASP-oriented detectors (injection, XSS, weak cryptography, redirects, CSRF) with the same policy: any warning breaks the build, and a false positive gets a narrow exclusion with its reason.

A local trial run on 2026-11-27 (commit `3bc1cb9`) reported five warnings: one is fixed, four are false positives.

| Pattern | Where | Decision |
| ------- | ----- | -------- |
| `XSS_SERVLET` | `ApiSecurityConfig.writeProblem` | **Fix**: the JSON is built with `String.formatted` without escaping; today every value is a constant, but a future detail with quotes would produce invalid JSON. Serialize a `ProblemDetail` instead |
| `SPRING_CSRF_PROTECTION_DISABLED` | `ApiSecurityConfig` | Exclude: the API is stateless with Bearer tokens, there is no cookie for a cross-site request to ride on (ADR 0015) |
| `SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING` | `AccessDeniedController` | Exclude: Spring Security forwards the 403 page with the original method; it changes no state |
| `UNVALIDATED_REDIRECT` ×2 | `OAuth2LoginFailureHandler` | Exclude: both targets are fixed paths of the application (context path + constant) |

### Out of scope

- Other analysers (Semgrep, CodeQL).
- Lowering the threshold or adding detectors beyond the plugin's defaults.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.rest` | `src/main/java/com/dawnrock/ddd/adapter/in/rest/ApiSecurityConfig.java` | `writeProblem` serializes a `ProblemDetail` |
| `adapter.in.rest` (test) | `src/test/java/com/dawnrock/ddd/adapter/in/rest/ApiSecurityTests.java` | 401/403/503 bodies are valid problem JSON |
| build | `pom.xml` | `findsecbugs.version` property and the plugin inside the SpotBugs `<plugins>` |
| build | `spotbugs-exclude.xml` | Four exclusions, by class and pattern, each with its reason |
| docs | `docs/adr/0020-static-analysis.md` | Find Security Bugs as a new build dependency |

- Spring Boot 4 uses Jackson 3 (`tools.jackson.databind`); reuse the application's `JsonMapper` bean (or `ObjectMapper` in whatever form the context exposes) instead of creating one, so the format matches `RestExceptionHandler`'s `ProblemDetail` responses.
- The plugin is a build-time dependency only: nothing reaches the jar.

## Tasks

### [x] T1 — Problem responses of the API chain serialized, not formatted (~10 min)

- **Layer:** adapter.in
- **Files:** `ApiSecurityConfig.java`, `ApiSecurityTests.java`
- **Depends on:** —
- **What to do:**
  1. Red: a test in `ApiSecurityTests` that parses the 401 (no token), 401 (invalid token), 403 (no role) bodies as JSON and checks `type`, `title`, `status` and `detail`, plus a unit test of the writer with a detail that contains `"` and `\` (still valid JSON, same text after parsing).
  2. Green: `writeProblem` builds a `ProblemDetail` (`ProblemDetail.forStatusAndDetail`, `setTitle`) and writes it with the JSON mapper to the response (`application/problem+json`, UTF-8). The mapper reaches the entry point and the access denied handler through the bean method's parameters.
- **Acceptance criteria:**
  - [x] The new tests pass; existing `ApiSecurityTests`, `Auth0ApiTokensTests` and `ApiAuth0UnavailableTests` still pass with the same bodies.
  - [x] `node scripts/contract-test.mjs` (Schemathesis) still passes: the bodies keep the contract's `Problem` schema.
- **Deviation:** the writer serializes an ordered map with the same four fields (`type`, `title`, `status`, `detail`) through a private `JsonMapper` constant, instead of a `ProblemDetail` through the application's bean: the body stays byte-for-byte the same as before for every current message, needs no `ProblemDetail` mixin and no wiring. The new test is `ApiProblemResponsesTests` (red: the old writer produced invalid JSON for a detail with quotes). The 401 (no token), 401 (invalid token) and 403 (no role) bodies were already parsed by the existing `jsonPath` assertions of `ApiSecurityTests`, which pass unchanged, so no duplicate test was added; the writer is package-private only for its unit test.

### [x] T2 — Find Security Bugs in SpotBugs, with narrow exclusions (~10 min)

- **Layer:** build
- **Files:** `pom.xml`, `spotbugs-exclude.xml`
- **Depends on:** T1
- **What to do:**
  1. `<findsecbugs.version>1.14.0</findsecbugs.version>` next to the other analysis versions, and the plugin in the SpotBugs `<configuration><plugins>`, with a comment pointing to ADR 0020 and #205.
  2. Run `./mvnw verify`: only the four false positives above must remain (if new ones appear, triage them: fix a real one, or stop and add it to the plan with its reason).
  3. One `<Match>` per false positive (three blocks: the two `UNVALIDATED_REDIRECT` warnings share `onAuthenticationFailure`; the exclusion is by method, not by line) with `<Class name="…"/>` and `<Bug pattern="…"/>` (and `<Method>` for `AccessDeniedController.forbidden`), each with a comment explaining why, in the file's style.
  4. Validate the exclusions: remove one temporarily and check that `./mvnw verify` fails with that warning.
- **Acceptance criteria:**
  - [x] `./mvnw verify` ends in `BUILD SUCCESS` with the plugin active (the SpotBugs log lists `findsecbugs-plugin`).
  - [x] No exclusion covers a whole package or a whole pattern without a class.

### [x] T3 — ADR and project status (~5 min)

- **Layer:** docs
- **Files:** `docs/adr/0020-static-analysis.md`, `docs/ia/decisiones.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`
- **Depends on:** T2
- **What to do:**
  1. ADR 0020: a dated section "Find Security Bugs (#205)" with why (OWASP detectors on the analysis we already run), the version, and the triage table of the first run.
  2. `decisiones.md` entry; status: #205 done, next step #206; one row in `historial.md`.
- **Acceptance criteria:**
  - [x] The CI status check passes (the PR closes #205 and touches the status file).

## Global acceptance criteria

**Specific:**

- [x] Find Security Bugs runs in `./mvnw verify` and in the CI, and a new warning breaks the build.
- [x] The four false positives are excluded one by one, with their reason.
- [x] The API chain's problem responses are serialized JSON.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR): `findsecbugs-plugin`, justified in ADR 0020.
- [x] If the API changes: n/a (same status codes and `Problem` bodies; the contract does not change).
- [x] Any new business term is in `docs/dominio/glosario.md` — n/a.
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- After T1, Find Security Bugs may flag the new writer differently (for example through `getOutputStream`); triage it in T2 like any other warning.
- If #206 lands first, its new classes (nonce, header writers) may raise their own warnings; whichever PR goes second triages them.

### [x] T4 — Review corrections (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/historial.md`, this plan
- **Depends on:** T3
- **What to do:**
  1. `revisor-ddd`: the history row said "four exclusions" while `spotbugs-exclude.xml` has three `<Match>` blocks for four warnings; the plan's T1 did not say which tests parse the 401/403 bodies. Both texts corrected.
- **Acceptance criteria:**
  - [x] History, plan and `spotbugs-exclude.xml` agree.

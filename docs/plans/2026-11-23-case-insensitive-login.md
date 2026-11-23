# Plan: Case-insensitive username on login

- **Issue:** [#157](https://github.com/dawnrock/Proyect-DDD-Claude/issues/157)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** in progress
- **Total estimate:** ~40 min (5 tasks)

## Goal

Since #150 the users live in PostgreSQL through `JdbcUserDetailsManager`, which looks them up with `where username = ?`. That comparison is case-sensitive in PostgreSQL (and in H2 in PostgreSQL mode). So a customer who signed up as `elber` cannot log in by typing `Elber`, and gets the same "wrong username or password" message as a wrong password.

**Current:** `Elber` or ` elber ` fails on the web login and on `POST /api/auth/token`. Three failures also lock `elber` from that IP for 30 minutes, because `LoginAttempts` already lower-cases its key.

**Expected:** the login strips surrounding spaces and lower-cases the username (`Locale.ROOT`) before the lookup, on the web and on the API. This brings back the behaviour that `decisiones.md` (#152) describes as "signing in was already case-insensitive (`InMemoryUserDetailsManager`)". The username search and the attempt limit use the same normalisation.

### Out of scope

- The API sign-up (`POST /api/customers`) keeps rejecting `Elber`: #152 decided that only the web forms normalise.
- No data migration: every stored username already matches `[a-z0-9._-]{3,30}` (the sign-up and the configured users are validated against it), so they are all lower case.
- No database-side change (`citext`, `lower(username)` index or a custom `usersByUsernameQuery`).

## Context

The single entry point for both logins is `ThrottledAuthenticationProvider`: the web form login and `AccessTokenIssuer` (API tokens) both authenticate through it (#130, ADR 0017). Normalising there, before the attempt reservation and before delegating to `DaoAuthenticationProvider`, fixes both at once and keeps the throttling key and the lookup in step. The principal returned is the stored `UserDetails` username, so the session, the JWT `sub` and the ownership checks (`Caller`) all get `elber`.

The existing web and token tests use `TestUsers` (an in-memory store, case-insensitive), so they cannot reproduce the bug. The reproducing tests need the real `UserStore` on an H2 database migrated by Flyway in PostgreSQL mode, as `SecuritySupportTests` already does.

The normalisation already exists for the web forms (`SignUpForm.normalizeUsername`: `strip()` + `toLowerCase(Locale.ROOT)`) and in `LoginAttempts.pairKey` (`trim()` + `toLowerCase(Locale.ROOT)`). This plan moves it into one shared helper in `adapter.in`, so every place applies the same rule.

No domain, application or persistence code changes. The layering is unchanged (everything stays in `adapter.in`).

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in` | `src/main/java/com/pronoide/ddd/adapter/in/Usernames.java` (new) | `normalize(String)`: strip + lower case `Locale.ROOT`, `null` stays `null` |
| `adapter.in` | `src/main/java/com/pronoide/ddd/adapter/in/ThrottledAuthenticationProvider.java` | Normalise the name into a new unauthenticated token that keeps the details (IP) before throttling and delegating |
| `adapter.in` | `src/main/java/com/pronoide/ddd/adapter/in/LoginAttempts.java` | `pairKey` uses `Usernames.normalize` |
| `adapter.in.web` | `src/main/java/com/pronoide/ddd/adapter/in/web/SignUpForm.java` | `normalizeUsername` delegates to `Usernames.normalize` |
| tests | `src/test/java/com/pronoide/ddd/adapter/in/CaseInsensitiveLoginTests.java` (new), `ThrottledAuthenticationProviderTests.java`, a full-context login test | Reproduce the bug on the real JDBC store; web and API end to end |
| docs | `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md` | Describe `TokenRequest.username` as case-insensitive (patch 2.2.3) |
| docs | `docs/ia/*` | Fix the #152 note, decision for #157, status |

## Tasks

### [x] T1 — Reproduce on the JDBC store and normalise in the provider (~10 min)

- **Layer:** adapter.in
- **Files:** `src/test/java/com/pronoide/ddd/adapter/in/CaseInsensitiveLoginTests.java` (new), `src/main/java/com/pronoide/ddd/adapter/in/Usernames.java` (new), `src/main/java/com/pronoide/ddd/adapter/in/ThrottledAuthenticationProvider.java`
- **Depends on:** —
- **What to do:**
  1. Red: build the real chain as `SecuritySupport` does: an H2 database (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`) migrated by Flyway, `UserStore.userDetailsService(...)` with a user `elber`, a `DaoAuthenticationProvider` with BCrypt, and a `ThrottledAuthenticationProvider` over it. Authenticating `Elber` and ` elber ` with the right password succeeds and the principal's name is `elber`. Check that it fails before the fix.
  2. Green: add `Usernames.normalize(String)` (`strip()` + `toLowerCase(Locale.ROOT)`, `null` stays `null`). In `authenticate`, build `UsernamePasswordAuthenticationToken.unauthenticated(normalized, credentials)`, copy the original details onto it, and use it for `tryAttempt`, the delegate, `succeeded`, `released` and the log.
- **Acceptance criteria:**
  - [x] `CaseInsensitiveLoginTests` passes: `Elber` and ` elber ` log in as `elber`. A wrong password is still `BadCredentialsException`.
  - [x] An unknown username (`Nadie`) still fails with the same `BadCredentialsException` as a wrong password (no user enumeration).

### [x] T2 — Provider unit tests: normalised name, details kept, shared lockout (~5 min)

- **Layer:** adapter.in
- **Files:** `src/test/java/com/pronoide/ddd/adapter/in/ThrottledAuthenticationProviderTests.java`, `src/test/java/com/pronoide/ddd/adapter/in/UsernamesTests.java` (new)
- **Depends on:** T1
- **What to do:**
  1. The delegate receives `elber` when `" Elber "` is typed, and the details (IP) are the original ones.
  2. Three failures with `Elber` lock `elber` from the same IP (documents the shared key).
  3. `UsernamesTests`: spaces and capitals, `null`, and a name that is already normalised.
- **Acceptance criteria:**
  - [x] The new tests pass and the existing `ThrottledAuthenticationProviderTests` stay green.

### [ ] T3 — One normalisation for the attempt limit and the web forms (~5 min)

- **Layer:** adapter.in
- **Files:** `src/main/java/com/pronoide/ddd/adapter/in/LoginAttempts.java`, `src/main/java/com/pronoide/ddd/adapter/in/web/SignUpForm.java`
- **Depends on:** T1
- **What to do:**
  1. `LoginAttempts.pairKey` uses `Usernames.normalize` (`strip()` instead of `trim()`), keeping the `null` → `""` handling and the length cut.
  2. `SignUpForm.normalizeUsername` delegates to `Usernames.normalize`, or callers use the helper directly (`OpenAccountForm`). Keep the behaviour.
- **Acceptance criteria:**
  - [ ] `LoginAttemptsTests`, `SignUpForm` and `OpenAccountForm` tests stay green with no change in behaviour.

### [ ] T4 — End to end on the web and the API with the real store (~10 min)

- **Layer:** tests
- **Files:** a full-context test that boots `DddLabApplication` with the `demo` profile and its own H2 database (in the style of `PersistenceRestartTests`), for example `src/test/java/com/pronoide/ddd/CaseInsensitiveLoginEndToEndTests.java`
- **Depends on:** T1
- **What to do:**
  1. Web: `POST /login` with `Ada` and the demo password (README) redirects to `/` and the authenticated name is `ada`. Use its own IP (`setRemoteAddr`) as `decisiones.md` asks for tests that fail logins.
  2. API: `POST /api/auth/token` with `{"username":" ADA ","password":…}` answers 200 and the JWT `sub` is `ada`.
- **Acceptance criteria:**
  - [ ] Both tests pass on the real `JdbcUserDetailsManager` (H2 in PostgreSQL mode) and failed before T1.

### [ ] T5 — Contract and project documentation (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`
- **Depends on:** T1-T4
- **What to do:**
  1. `TokenRequest.username`: add a description ("case-insensitive; surrounding spaces are ignored"). `info.version` 2.2.3 (documentation only, ADR 0014) and its `CHANGELOG.md` entry. Run `npx @redocly/cli lint docs/api/openapi.yaml` and `node scripts/contract-test.mjs`.
  2. `decisiones.md`: correct the #152 entry (since #150 the JDBC store is case-sensitive) and add a #157 entry. Logins normalise the username in `ThrottledAuthenticationProvider` with `Usernames.normalize`, and the API sign-up stays strict.
  3. `pendientes.md`, `estado-del-proyecto.md` (short) and a row in `historial.md`. If `glosario.md` describes the username, add the rule.
- **Acceptance criteria:**
  - [ ] Redocly has no errors, the contract test passes and the CI bump check accepts 2.2.3.
  - [ ] Project status updated; all new text in English.

## Global acceptance criteria

**Specific:**

- [ ] A customer who signed up as `elber` logs in with `Elber` or ` elber ` on the web and through `POST /api/auth/token`.
- [ ] An unknown username still gets the same answer as a wrong password, and the attempt limit treats `Elber` and `elber` as the same user.
- [ ] After deploying to Render with Neon, the user checks the login with a capitalised username.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes. (Applies as documentation only: 2.2.3.)
- [ ] Any new business term is in `docs/dominio/glosario.md`. (n/a: no new term.)
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **API sign-up:** keeping `POST /api/customers` strict (decision #152) means `Elber` is rejected there but accepted on login. That is consistent, because the stored username is always lower case. Revisit only if the user wants the API to normalise too.
- **Configured users:** `SecuritySupport` already rejects a configured username outside the pattern at startup, so no configured user can have capitals that the normalisation would hide.
- **Throttling key:** switching `pairKey` from `trim()` to `strip()` only changes Unicode whitespace, which no valid username contains.

# Plan: Limit login attempts on the web and the API

- **Issue:** [#130](https://github.com/dawnrock/Proyect-DDD-Claude/issues/130)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-20
- **Status:** done
- **Total estimate:** ~95 min (9 tasks)

## Goal

The application is public on Render and the demo user names are in the repository, yet nothing limits login attempts on the web (`/login`, pending since #50) or on the API (`POST /api/auth/token`, pending since #122). After a few failures, the attempts are blocked for a while, even with the right password, on both entry points and without revealing whether the user exists. No new dependency and no change to the domain or `application`: it lives in the input adapters' shared security support.

**Decisions of the user (2026-11-20):**

- **Policy:** block the pair *user + IP* and, separately, an *IP* that fails with any user (OWASP). An attacker on another IP cannot lock the real administrator out.
- **Thresholds:** 3 failures per user + IP, 10 per IP, blocked for 30 minutes. Configurable.
- **Customer sign-up** is out of scope: [#131](https://github.com/dawnrock/Proyect-DDD-Claude/issues/131).

### Out of scope

- Sign-up limits (#131).
- Shared counters across instances (Render runs one instance; the counters live in memory and reset on restart).
- CAPTCHA, user notifications or permanent account locking.
- `Monolithic`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| adapter.in (shared security support) | `LoginAttempts` (new), `LoginAttemptsProperties` (new) | In-memory counters keyed by user + IP and by IP, with the `Clock` bean. `lockedFor(user, ip)` returns the time left; `failed(user, ip)` and `succeeded(user, ip)` update them. A failure counts while it is younger than the lockout window; a success clears the pair (not the IP). User names are compared in lower case. Expired entries are pruned on access so the maps stay small |
| adapter.in | `ThrottledAuthenticationProvider` (new), `TooManyLoginAttemptsException` (new, extends `AuthenticationException`) | Wraps the `DaoAuthenticationProvider`: reads the IP from the authentication details (`WebAuthenticationDetails`), refuses before checking the password while locked, and records failures and successes. Logs a warning with the user and IP when a lock starts |
| adapter.in | `SecuritySupport` | `AuthenticationProvider` bean built on the existing users and `PasswordEncoder`, used by the web login |
| adapter.in.web | `WebSecurityConfig`, `LoginController`, `login.html` | A failure handler sends `TooManyLoginAttemptsException` to `/login?blocked` (other failures keep `/login?error`); the page says "Demasiados intentos fallidos. Vuelve a intentarlo dentro de 30 minutos." with the configured minutes |
| adapter.in.rest | `AccessTokenIssuer`, `TokenController`, `RestExceptionHandler` | The issuer uses the throttled provider and passes the request's details; a lock answers 429 `ProblemDetail` with `Retry-After` in seconds |
| config | `application.properties` | `app.security.login-attempts.*` and `server.forward-headers-strategy=native`, so behind Render's proxy the IP is the client's (Tomcat takes the last untrusted address of `X-Forwarded-For`; a client cannot spoof it by adding its own) |
| docs | `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `scripts/contract-test.mjs` | 429 on `issueToken`, contract 2.1.0; the contract-test run raises the thresholds so that Schemathesis's random credentials do not lock its own IP |
| docs | `docs/adr/0017-login-attempt-limit.md` (new), `docs/ia/*` | Decision, alternatives (Bucket4j, a reverse proxy, per-user only) and consequences |

**Why a provider wrapper:** both entry points end in an `AuthenticationProvider` (the web through Spring Security's form login, the API through `AccessTokenIssuer`'s own `ProviderManager`), and the authentication details carry the IP. One class covers both, with no servlet filter that would have to read the API's JSON body.

**Architecture:** the new classes sit in the root of `adapter.in`, the shared security support that ArchUnit already allows (ADR 0008); they do not know `rest` or `web`. No new rule.

## Tasks

### [x] T1 — Login attempt counters (~10 min)

- **Layer:** adapter.in
- **Files:** `LoginAttempts`, `LoginAttemptsProperties`, `LoginAttemptsTests`
- **Depends on:** —
- **What to do:**
  1. Tests first (plain JUnit with a fixed, movable `Clock`):
     - after 3 failures the pair is locked for 30 minutes, and another IP for the same user is not;
     - after 10 failures from one IP with different users, that IP is locked for any user;
     - a success before the limit clears the pair;
     - the lock ends after 30 minutes;
     - the user name is case-insensitive;
     - old failures fall out of the window.
  2. Implement with `ConcurrentHashMap` and the `Clock` bean; properties `max-per-user` (3), `max-per-ip` (10), `lockout` (30m) under `app.security.login-attempts`.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T2 — Throttled authentication provider (~10 min)

- **Layer:** adapter.in
- **Files:** `ThrottledAuthenticationProvider`, `TooManyLoginAttemptsException`, `ThrottledAuthenticationProviderTests`
- **Depends on:** T1
- **What to do:**
  1. Tests first, with a stub delegate:
     - a wrong password records a failure and rethrows it;
     - a correct one records a success;
     - while locked, it throws `TooManyLoginAttemptsException` with the time left *without* calling the delegate, even with the right password;
     - an unknown user counts the same as a known one (same exception type and message);
     - no details means the IP is `unknown`.
  2. Implement; log a warning when a lock starts.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T3 — Web login (~10 min)

- **Layer:** adapter.in (config) · adapter.in.web
- **Files:** `SecuritySupport`, `WebSecurityConfig`, `LoginController`, `login.html`, `WebSecurityTests`, `LoginControllerTests`
- **Depends on:** T2
- **What to do:**
  1. Tests first:
     - three wrong form logins for `ada` from one IP, then the right password, redirect to `/login?blocked`;
     - `/login?blocked` shows the message with the configured minutes;
     - a wrong login still redirects to `/login?error`;
     - a correct login still works.
  2. Provider bean in `SecuritySupport`; failure handler in the web chain; message in the template (`alert-danger` with an icon).
- **Acceptance criteria:**
  - [x] Tests green; the existing login tests unchanged.

### [x] T4 — API token: 429 with Retry-After (~10 min)

- **Layer:** adapter.in.rest
- **Files:** `AccessTokenIssuer`, `TokenController`, `RestExceptionHandler`, `TokenControllerTests`
- **Depends on:** T2
- **What to do:**
  1. Tests first:
     - three wrong `POST /api/auth/token` calls and then the right one answer 429 `ProblemDetail` with `Retry-After` and a detail that does not reveal whether the user exists;
     - wrong credentials before the limit still answer 401.
  2. The issuer uses the throttled provider and sets `WebAuthenticationDetails` from the request; the handler maps the exception.
- **Acceptance criteria:**
  - [x] Tests green; the existing token tests unchanged.

### [x] T5 — Real client IP behind the proxy (~10 min)

- **Layer:** config
- **Files:** `application.properties`, `DddLabApplicationTests`
- **Depends on:** T3
- **What to do:**
  1. Test first, with the real server of `DddLabApplicationTests`. Three wrong logins with `X-Forwarded-For: 203.0.113.5` lock that address, and the same user with `X-Forwarded-For: 203.0.113.6` can still log in.
  2. `server.forward-headers-strategy=native`.
- **Acceptance criteria:**
  - [x] Test green; seen red without the setting (every request then shares the loopback address).

### [x] T6 — OpenAPI contract 2.1.0 and contract tests (~10 min)

- **Layer:** docs · scripts
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `scripts/contract-test.mjs`
- **Depends on:** T4
- **What to do:**
  1. A `TooManyRequests` response (429, `ProblemDetail`, `Retry-After` header) on `issueToken`; `info.version` 2.1.0 (an added response, not breaking, ADR 0014); CHANGELOG entry.
  2. `contract-test.mjs` starts the jar with high thresholds, because Schemathesis's random credentials would otherwise lock its own IP and its token request.
  3. `npx @redocly/cli lint docs/api/openapi.yaml`, `node scripts/check-contract-version.mjs` and `node scripts/contract-test.mjs`.
- **Acceptance criteria:**
  - [x] Lint without errors, version check and contract tests green.

### [x] T7 — Browser check (~5 min)

- **Layer:** tests (manual, Playwright)
- **Files:** —
- **Depends on:** T3
- **What to do:**
  1. In Chromium, three wrong logins and then the right one show the blocked message; another user from the same browser can still log in until the IP reaches 10 failures.
  2. With JavaScript (boosted form, #126) and without it.
- **Acceptance criteria:**
  - [x] Both behave as described.

- **Result (Playwright, 2026-11-20):**
  - **With JavaScript (boosted form):** three wrong logins for `ada` end on `/login?error`; the right password then ends on `/login?blocked` with "Demasiados intentos fallidos. Vuelve a intentarlo dentro de 30 minutos."; `grace` logs in from the same browser.
  - **Without JavaScript:** the same with `alan`, and then `admin`.
  - **Log:** it shows `Bloqueados los inicios de sesión de 'ada' desde 127.0.0.1 …`.
  - **Console:** no page errors.

### [x] T8 — ADR 0017 and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0017-login-attempt-limit.md`, `docs/adr/README.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`, `docs/ia/estado-del-proyecto.md`, `docs/contracts/login-roles.md` (if it lists the token responses)
- **Depends on:** T1-T7
- **What to do:**
  1. ADR 0017. Decision, alternatives (Bucket4j or another library; a limit in a reverse proxy, which Render's free plan lacks; per-user only, which lets anyone lock the administrator out; per-IP only) and consequences:
     - counters in memory, reset on restart and not shared across instances;
     - memory bounded by pruning;
     - a shared IP (NAT) can be blocked as a whole after 10 failures.
  2. `pendientes.md`: remove the "no limit on login attempts" of #50 and #122. Add:
     - a check on Render that the logged IP is the client's;
     - the limits of an in-memory counter.
  3. Next step: #131.
- **Acceptance criteria:**
  - [x] The CI status-file check passes.

### [x] T9 — Review fixes (~20 min)

- **Layer:** adapter.in · adapter.in.web · tests · docs
- **Files:** `LoginAttempts`, `ThrottledAuthenticationProvider`, `LoginController`, `LoginAttemptsTests`, `ThrottledAuthenticationProviderTests`, `TokenControllerTests`, `docs/ia/pendientes.md`
- **Depends on:** T1-T8
- **What to do:** findings of the `revisor-ddd` review (none blocking):
  1. **Bursts of concurrent requests.** Checking the lock and recording the failure were separate steps, so a burst of simultaneous requests could test more passwords than the limit. `LoginAttempts.tryAttempt` now reserves each attempt as a failure, in the same atomic step that checks the lock, before the password is checked. A success clears the pair and gives back the IP's reservation (`succeeded`). A rejection that is not about credentials (a disabled account…) gives back both (`released`). New tests:
     - ten concurrent wrong attempts: only 3 reach the password check. Seen red by checking the lock without reserving, then restored;
     - a `DisabledException` does not count.
  2. **Race with pruning.** `LoginAttempts` is now fully `synchronized` over plain maps. This also closes the race in which pruning could drop a deque while a failure was being recorded in it.
  3. **Bounded memory.** The user name is cut to 128 characters in the key (test with a 5,000-character name).
  4. **Clean logs.** The log replaces every control character, U+2028 and U+2029, and truncates the user name.
  5. **Stable test.** The `Retry-After` assertion accepts 1700-1800 seconds, so it does not depend on the time between two requests.
  6. **One source for the minutes.** `LoginController` takes the minutes from `LoginAttemptsProperties`, rounded up and at least 1, instead of repeating the default.
  7. **Pending item.** A client on an internal address can choose the IP that counts, and a CDN in front would make everyone share one IP.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green; the browser check re-run.

## Global acceptance criteria

**Specific:**

- [x] After 3 failures the user + IP pair, and after 10 the IP, are blocked for 30 minutes on the web and the API, even with the right password.
- [x] A success before the limit resets the pair.
- [x] Neither the message nor the status reveals whether the user exists.
- [x] Behind Render's proxy the counted IP is the client's, not the proxy's.
- [x] Contract tests still green.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR): none added.
- [x] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [x] Any new business term is in `docs/dominio/glosario.md`: n/a (a technical safeguard, not a business rule).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Render's proxy address:** with `native`, Tomcat trusts `X-Forwarded-For` only when the connection comes from an internal address (10/8, 172.16/12, 192.168/16, 100.64/10, loopback…). If Render's proxy reached the container from another range, every request would share the proxy's IP and 10 failures would block everyone. The lock warning logs the IP, so the first deploy can be checked in Render's logs; if needed, `server.tomcat.remoteip.internal-proxies` is set to Render's range.
- **Shared IP (offices, NAT):** 10 failures from one network block everyone behind it for 30 minutes. Accepted for a practice project; the threshold is configurable.
- **Tests that log in many times:** `DddLabApplicationTests` and `WebSecurityTests` log in repeatedly with right passwords (successes do not count). Only failures count, so existing tests should stay green; any test that fails logins on purpose uses its own user or IP.

# Plan: Realistic mode in production — only Auth0, no local authentication

- **Issue:** [#199](https://github.com/dawnrock/Proyect-DDD-Claude/issues/199)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-27
- **Status:** in progress
- **Total estimate:** ~100 min (11 tasks) + the Auth0/Render set-up before the merge

## Goal

One code base, two modes chosen by configuration with `app.auth.local` (ADR 0024): **local** (`demo`, `default`, tests, Schemathesis: local users, `POST /api/auth/token`, form login, sign-up with a password) and **realistic** (`prod`, Render: only Auth0). In realistic mode the bank never signs anyone in with a local password: the web enters only with Auth0, the API only accepts Auth0 access tokens (with `aud`), new customers sign up only through Auth0 (#198), and the local password is checked only once, on the link page, for customers who existed before. A realistic mode without the full Auth0 configuration does not start.

Last of four issues: #196 (done) → #197 (done) → #198 (done) → **#199**.

**Already in place** (#196-#198 prepared the switch): `AuthProperties.local` (default `true`); the form login only with it (`WebSecurityConfig`); `TokenController` and `AccessTokenIssuer` only with it, and the own issuer only trusted with it (`ApiSecurityConfig`, tested in `LocalTokensDisabledTests`); the login page with only «Entrar con Auth0» and the «Ya soy cliente» door going to Auth0 (`LoginController`, `login.html`, `LoginAuth0OnlyTests`). **Missing:** the switch in `prod`, the startup check, the sign-ups with a password (REST and web classic wizard), the «Quiero ser cliente» door, the 401 hint that still points to `POST /api/auth/token`, an end-to-end test of the mode, and the docs.

**Fix found while setting up Render (2026-11-27, added as T1 at the user's request):** the first Auth0 sign-in on Render landed on `/login` with **both** notices, «No se pudo iniciar sesión con Auth0» and «Vuelve a entrar con Auth0 para vincular tu usuario». `login.html` (#196) tests the parameters with `${param.containsKey('oauth-error')}` / `${param.containsKey('link-expired')}`, which is always true: either parameter shows both notices (checked with a throwaway test; `${param['oauth-error'] != null}` shows only the right one). The tests only asserted that the expected notice appears, never that the other does not. Besides, the real cause of that first failure is unknown: `OAuth2LoginFailureHandler` redirects without logging anything (likely the instance switch of the redeploy losing the in-memory session that holds the authorization request; a second try worked and linked the user).

### Decisions agreed with the user (issue + 2026-11-27)

| Piece | `app.auth.local=true` | `app.auth.local=false` (`prod`) |
| --- | --- | --- |
| `POST /api/auth/token`, HS256 | Active | No bean → 404 |
| Tokens the API accepts | Own and, if configured, Auth0 | Only Auth0 (with `aud`) |
| Web login with a password | Active (plus Auth0 if configured) | Off: only «Entrar con Auth0» |
| Sign-up with a password (web classic wizard and `POST /api/customers`) | Active | Off: only sign-up through Auth0 |
| Auth0 variables | Optional (all-or-nothing rules of #196/#197) | **All five required**: `APP_AUTH_AUTH0_DOMAIN`, `_CLIENT_ID`, `_CLIENT_SECRET`, `_AUDIENCE`, `_API_DOCS_CLIENT_ID`; any missing → startup fails naming them |

- Administrators enter through Auth0 too, with the role from our database (they link once on `/login/link`).
- **Render set-up before the merge** (the user's choice): today the `Hexagonal` service has the three web variables but not `APP_AUTH_AUTH0_AUDIENCE` nor `APP_AUTH_AUTH0_API_DOCS_CLIENT_ID`. The user creates the API and the Single Page Application in Auth0 (`docs/deploy/render.md`, «The API with Auth0 tokens») and gives Claude both identifiers (not secret); Claude adds them to Render through its API, with the user's confirmation, **before** merging. The current code (#197) already uses them, so that deploy is safe and also checks #197 on Render. Only then is the PR merged; otherwise Render's deploy would fail at startup and keep the previous version.

### Out of scope

- `screen_hint=signup` on Auth0's `/authorize` so «Quiero ser cliente» opens Auth0's sign-up tab directly: the door goes to the normal Auth0 page, which has its own sign-up link.
- Hiding the `bearerAuth` scheme in Swagger UI in realistic mode: it stays documented as «local authentication only».
- Unlinking an identity, and the half-finished sign-up of #198 (`pendientes.md`).
- `Monolithic`: it has no Auth0 and does not change.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.web` | `templates/login.html`, `OAuth2LoginFailureHandler.java` | Fix (T1): each notice only with its own parameter; the failed Auth0 return logs its OAuth error code |
| `adapter.in` | `Auth0Properties.java` | `missingForRealisticMode()`: the names of the five variables without a value |
| `adapter.in` | `SecuritySupport.java` (or a small `RealisticModeCheck`) | With `app.auth.local=false`, fails at startup if any of the five is missing |
| `adapter.in` | `CustomerRegistration.java` | The password sign-up (`register(command, password, ip)`) refuses to run without local authentication (defence in depth) |
| `adapter.in.rest` | `CustomerController.java` | `@ConditionalOnBooleanProperty(name = "app.auth.local", matchIfMissing = true)` → 404 in realistic mode |
| `adapter.in.rest` | `ApiSecurityConfig.java` | The 401 without a token points to Auth0, not to `POST /api/auth/token`, in realistic mode |
| `adapter.in.web` | `SignUpController.java` | Without local authentication the classic wizard goes to Auth0; only the Auth0 mode (`?auth0`, #198) works |
| `adapter.in.web` | `templates/login.html`, `LoginController.java` | «Quiero ser cliente» goes to Auth0 when local authentication is off |
| `resources` | `application-prod.properties` | `app.auth.local=false` |
| tests | `ProdProfileTests`, new `RealisticModeTests` (`@SpringBootTest`), `LocalTokensDisabledTests`, `SignUpAuth0*`, `LoginAuth0OnlyTests` | See tasks; test tenant `TestAuth0Tenant` (fixed RSA key, no network) |
| docs | `openapi.yaml`, `CHANGELOG.md`, ADR 0024, `docs/deploy/render.md`, `docs/ia/*` | See tasks |

**No ArchUnit change:** everything stays inside `adapter.in` and `resources`; no new dependency.

**`ProdProfileTests` must change:** with `app.auth.local=false` in `prod`, its tests that start the application need the five Auth0 properties (`TestAuth0Tenant.PROPERTIES` plus web client id/secret and the SPA client id); the one that expects the JDBC URL failure keeps expecting it (with Auth0 given, so the failure is still the database's).

## Tasks

### [x] T1 — Fix: the login notices of Auth0 appear only with their parameter, and the failed return is logged (~10 min)

- **Layer:** adapter.in.web
- **Files:** `src/main/resources/templates/login.html`, `src/main/java/com/dawnrock/ddd/adapter/in/web/OAuth2LoginFailureHandler.java`; `LoginAuth0Tests.java`, `OAuth2LoginFailureHandlerTests.java` (new or existing)
- **Depends on:** —
- **What to do:**
  1. Tests first (they fail today): `GET /login?oauth-error` shows the red notice and **not** «Vuelve a entrar con Auth0…»; `GET /login?link-expired` shows the amber notice and **not** «No se pudo iniciar sesión con Auth0…»; `GET /login?form` shows neither.
  2. In `login.html`, `th:if="${param['oauth-error'] != null}"` and `th:if="${param['link-expired'] != null}"`; check no other template uses `param.containsKey`.
  3. Test first: when the Auth0 return fails for a reason other than an unlinked identity, the handler logs one `WARN` with the OAuth error code (`OAuth2AuthenticationException#getError().getErrorCode()`, e.g. `authorization_request_not_found`, `invalid_state_parameter`), or the exception class if there is none, and still redirects to `/login?oauth-error`. Never tokens, `sub`, e-mail or the description of the error (it may carry personal data). An unlinked identity is not a failure worth a warning: no log.
  4. `@Slf4j` on the handler (Lombok is allowed outside the domain).
- **Acceptance criteria:**
  - [x] The new login tests pass and the old ones stay green.
  - [x] The handler test captures the `WARN` with the error code (e.g. with `OutputCaptureExtension`) and the redirect is unchanged.

### [x] T2 — The realistic mode requires the full Auth0 configuration (~10 min)

- **Layer:** adapter.in
- **Files:** `src/main/java/com/dawnrock/ddd/adapter/in/Auth0Properties.java`, `SecuritySupport.java` (or new `RealisticModeCheck.java` in the same package); tests `Auth0PropertiesTests`, `Auth0ClientRegistrationTests` (`ApplicationContextRunner`)
- **Depends on:** —
- **What to do:**
  1. Test first: `Auth0Properties.missingForRealisticMode()` lists, in a fixed order, the `APP_AUTH_AUTH0_*` names without a value (empty with all five).
  2. A bean (registered with the security support) that, with `app.auth.local=false` and something missing, throws `IllegalStateException("Sin autenticación local (app.auth.local=false) hace falta Auth0: faltan APP_AUTH_AUTH0_AUDIENCE, …")`. With `app.auth.local=true` it does nothing (the all-or-nothing rules of #196/#197 still apply).
- **Acceptance criteria:**
  - [x] Context with `app.auth.local=false` and no Auth0 → fails, the message names the five variables; with only the three web ones → names `_AUDIENCE` and `_API_DOCS_CLIENT_ID`.
  - [x] Context with `app.auth.local=false` and the five → starts; with `true` and none → starts.

### [x] T3 — `prod` turns local authentication off (~10 min)

- **Layer:** config (resources) · tests
- **Files:** `src/main/resources/application-prod.properties`, `src/test/java/com/dawnrock/ddd/ProdProfileTests.java`
- **Depends on:** T2
- **What to do:**
  1. Test first in `ProdProfileTests`: with `prod`, the database variables and the five Auth0 ones, `AuthProperties.local()` is `false`; with `prod` and the database but no Auth0, startup fails with T2's message.
  2. `app.auth.local=false` in `application-prod.properties`, with a comment (#199, ADR 0024) and the emergency switch `APP_AUTH_LOCAL=true`.
  3. Give the existing `ProdProfileTests` the Auth0 properties so they keep testing what they tested.
- **Acceptance criteria:**
  - [x] `./mvnw test -Dtest=ProdProfileTests` green, the new two included.
  - [x] `demo`/`default` profiles unchanged (`app.auth.local` is `true`).

### [x] T4 — The password sign-up refuses to run without local authentication (~5 min)

- **Layer:** adapter.in
- **Files:** `src/main/java/com/dawnrock/ddd/adapter/in/CustomerRegistration.java`; its test
- **Depends on:** —
- **What to do:**
  1. Test first: with `AuthProperties(false)`, `register(command, password, ip)` throws `IllegalStateException` and creates nothing (no account, no user, no sign-up counted); `registerWithExternalIdentity(…)` keeps working.
  2. Inject `AuthProperties` and add the guard. It is defence in depth: T5 and T6 make it unreachable from the outside.
- **Acceptance criteria:**
  - [x] The new test passes and the existing sign-up tests stay green.
  - Done: the test lives in its own `CustomerRegistrationRealisticModeTests` (one more type in `CustomerRegistrationTests` broke PMD's `CouplingBetweenObjects`); seen red by disabling the guard.

### [x] T5 — REST: no sign-up with a password and a 401 that points to Auth0 (~10 min)

- **Layer:** adapter.in.rest
- **Files:** `CustomerController.java`, `ApiSecurityConfig.java`; `LocalTokensDisabledTests.java`, `ApiSecurityTests.java`
- **Depends on:** T4
- **What to do:**
  1. Tests first in `LocalTokensDisabledTests` (add `CustomerController` to the slice): `POST /api/customers` → 404 with `app.auth.local=false`; `GET /api/accounts` without `Authorization` → 401 whose `detail` asks for an Auth0 access token and does not name `/api/auth/token`.
  2. `@ConditionalOnBooleanProperty(name = "app.auth.local", matchIfMissing = true)` on `CustomerController` (as on `TokenController`).
  3. In `ApiSecurityConfig`, choose the detail of the missing-token 401 by mode: local keeps today's text; realistic → «Hace falta autenticarse para usar la API: inicia sesión con Auth0 y envía su token de acceso como Bearer».
- **Acceptance criteria:**
  - [x] `POST /api/customers` → 404 in realistic mode; 201 in local mode (existing tests unchanged).
  - [x] Missing-token 401 detail per mode, tested in both.

### [x] T6 — Web: the classic sign-up wizard goes to Auth0 in realistic mode (~10 min)

- **Layer:** adapter.in.web
- **Files:** `SignUpController.java`; `SignUpAuth0Tests.java` (or a new `SignUpRealisticModeTests`)
- **Depends on:** T4
- **What to do:**
  1. Tests first with `app.auth.local=false` and the test tenant: `GET /signup` (no `?auth0`, no pending identity) → redirect to `/oauth2/authorization/auth0`; `POST /signup`, `/signup/access`, `/signup/account` with a classic form → the same redirect, nothing created; `GET /signup?auth0` with a pending identity still runs the Auth0 wizard of #198 end to end.
  2. Inject `AuthProperties`; when local authentication is off and the form is not in Auth0 mode, every step returns `redirect:/oauth2/authorization/auth0` (an abandoned Auth0 wizard already resets to step 1, which now means going to Auth0).
- **Acceptance criteria:**
  - [x] The new tests pass; `SignUpControllerTests` (local mode) unchanged and green.
  - Done: tests in a new `SignUpRealisticModeTests`; the controller reads `@Value("${app.auth.local:true}")` instead of `AuthProperties`, because one more type broke PMD's `CouplingBetweenObjects` on `SignUpController`.

### [ ] T7 — Web: «Quiero ser cliente» goes to Auth0 in realistic mode (~5 min)

- **Layer:** adapter.in.web
- **Files:** `src/main/resources/templates/login.html`; `LoginAuth0OnlyTests.java`
- **Depends on:** T1 (same template)
- **What to do:**
  1. Test first in `LoginAuth0OnlyTests`: the «Quiero ser cliente» door links to `/oauth2/authorization/auth0` with `hx-boost="false"` and its hint mentions Auth0; in `LoginControllerTests` (local) it still links to `/signup`.
  2. In `login.html`, the door follows `auth0Enabled and !localEnabled`, like «Ya soy cliente» (hint: «Crea tu acceso con Auth0 y abre tu cuenta en tres pasos.»).
- **Acceptance criteria:**
  - [ ] Both login tests green; the page has no form posting to `/login` in realistic mode (already covered, keep it).

### [ ] T8 — End-to-end test of the realistic mode, without network (~10 min)

- **Layer:** tests
- **Files:** new `src/test/java/com/dawnrock/ddd/RealisticModeTests.java` (`@SpringBootTest` + `MockMvc`, like `Auth0ApiTokensTests`)
- **Depends on:** T1-T7
- **What to do:**
  1. Properties: `app.auth.local=false`, `TestAuth0Tenant.PROPERTIES`, web client id/secret and SPA client id; link a demo user's `sub` in `setUp`.
  2. Check: `POST /api/auth/token` → 404; `POST /api/customers` → 404; `GET /login?form` has no password form; `POST /login` with valid demo credentials and CSRF does **not** authenticate; an Auth0 access token of a linked user → 200 on `GET /api/accounts`; an own HS256 token well signed → 401; `GET /signup` → redirect to Auth0.
- **Acceptance criteria:**
  - [ ] `./mvnw test -Dtest=RealisticModeTests` green; the existing end-to-end tests and Schemathesis still run in local mode, unchanged.

### [ ] T9 — OpenAPI: what exists only with local authentication (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`
- **Depends on:** T5
- **What to do:**
  1. `/api/auth/token` already says «available only while local authentication is on»; say the same for `POST /api/customers` (in production customers sign up on the web with Auth0, #198), and fix its «ask for a token with `POST /api/auth/token`» sentence for both modes. Add the realistic-mode example of the missing-token 401 next to the local one; `bearerAuth` described as local authentication only.
  2. Documentation only (no path, schema or status removed from the contract): `info.version` 2.3.0 → **2.3.1** (patch, ADR 0014; confirm with `oasdiff breaking`), entry in `CHANGELOG.md`.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors; `node scripts/check-contract-version.mjs` passes.
  - [ ] `node scripts/contract-test.mjs` passes (local mode).

### [ ] T10 — ADR 0024, the Render guide and the project status (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0024-oauth2-oidc-auth0.md`, `docs/deploy/render.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T1-T9
- **What to do:**
  1. ADR 0024: #199 done; the mode table above; the startup check; the password sign-ups off; `APP_AUTH_LOCAL=true` as an emergency switch (back to local mode without a deploy of code).
  2. `render.md`: section 2 ter is no longer «optional» for `Hexagonal`: a new subsection «Realistic mode (#199)» with the five required variables, the order (Auth0 API + SPA → variables in Render → deploy the code with #199), what changes for users (demo users and the administrator link once on `/login/link`, then only Auth0), the manual check and the rollback.
  3. Status files: the next step, the decision (five variables required in `prod`), the resolved `pendientes.md` notes «For #199» of #196 and #198, a new note on the Auth0 Management API (see Risks), and a row in `historial.md`.
  4. `render.md`, «The API with Auth0 tokens»: in the SPA's *API Access* tab, **User-delegated Access → Grant Access** for the API (the button's name in today's dashboard); without it the SPA cannot ask for tokens of an API with per-app authorization.
- **Acceptance criteria:**
  - [ ] Documents in English; the CI check of `estado-del-proyecto.md` passes.

### [ ] T11 — Auth0 and Render: set-up before the merge and check after it (~10 min + the user's time in Auth0)

- **Layer:** deploy (no code)
- **Files:** — (Render environment through `api.render.com`)
- **Depends on:** T10 (PR open and green); the user's identifiers
- **What to do:**
  1. ~~**Before merging:** the user creates in Auth0 the API and the Single Page Application of Swagger UI and gives the **API Identifier** and the **SPA Client ID**; Claude adds `APP_AUTH_AUTH0_AUDIENCE` and `APP_AUTH_AUTH0_API_DOCS_CLIENT_ID` to the `Hexagonal` service and deploys.~~ **Done on 2026-11-27** with the user's «yes»: API `monAbank` (identifier `https://mona-bank/`), SPA `monA Bank Swagger UI` with *Grant Access* on the API; deploy `dep-dc4l5avf3r2c73bq1700` (code `7feaa44`, #197) live, started without Auth0 configuration errors. The user linked their Auth0 identity on the web (second try, see T1). Pending: the user's check of Swagger UI «Authorize» with Auth0.
  2. Check again, right before merging, that the five variables are set on the service (names only, never values).
  3. **Merge** (step 7 bis) and follow Render's deploy until it is live: `GET /actuator/health` → `UP`; `POST /api/auth/token` → 404; `/login?form` shows only «Entrar con Auth0»; `POST /api/customers` → 404. The manual Auth0 checks (link a demo user, sign up, Swagger UI) are the user's (they need an Auth0 login).
- **Acceptance criteria:**
  - [ ] The deploy with #199 is live on Render and the checks above answer as expected; if it fails, `APP_AUTH_LOCAL=true` brings back local mode and the cause goes to `pendientes.md`.

## Global acceptance criteria

**Specific:**

- [ ] `application-prod.properties` with `app.auth.local=false`; starting `prod` without the five Auth0 variables fails with a clear message naming the missing ones.
- [ ] Realistic-mode test (test issuer with a fixed key, no network): `/api/auth/token` → 404, `POST /api/customers` → 404, no form login, a linked Auth0 token is accepted and an own token is rejected, the classic sign-up goes to Auth0.
- [ ] The end-to-end tests and Schemathesis still run in local mode, unchanged.
- [ ] `openapi.yaml`: `/api/auth/token` and `POST /api/customers` documented as available only with local authentication (development and tests); ADR 0024 updated; `docs/deploy/render.md` with the steps to turn the realistic mode on.
- [ ] Render's `Hexagonal` service runs the realistic mode with the five Auth0 variables.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new business term).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **The merge depends on the user's Auth0 set-up.** Without the two identifiers in Render, the deploy of #199 fails at startup (Render keeps the previous version, so nothing breaks, but #199 is not live). That is why T11 sets them **before** merging.
- **Demo users on Render:** `SPRING_PROFILES_ACTIVE=demo,prod`, and the four `APP_SECURITY_USERS_*` are set as Render variables. In realistic mode they are reachable only by linking an Auth0 identity once with their password; whoever knows a demo password can still link it (the same exposure as today's form login, but only once per user: a linked user cannot be linked again).
- **Mode-dependent 401 text** changes an error `detail` in production; clients should not parse it (RFC 9457), and the contract shows both examples.
- **`POST /login` in realistic mode** has no handler: the end-to-end test asserts only that it does not authenticate (the exact status, 403 without CSRF or 405 with it, is Spring's).
- **Not tried against a real tenant in CI:** as in #196-#198, the manual Auth0 checks after the deploy are the user's.
- **Auth0 Management API with user-delegated access for every application:** the tenant's policy for that system API is «allow all», so the SPA shows «All permissions granted» and it cannot be revoked per application, only by switching the API's policy to per-app authorization (a tenant-wide change). The scopes are only `*:current_user*` (each user's own Auth0 profile) and Swagger UI never asks for that audience, so it stays as is; noted in `pendientes.md` for a future hardening of the tenant.
- **Sessions in memory on Render:** an Auth0 sign-in that starts on one instance and comes back to another during a redeploy loses the authorization request (probable cause of the first failure, T1). T1's log will confirm it next time; nothing else changes here.

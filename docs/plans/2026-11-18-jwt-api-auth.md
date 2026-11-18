# Plan: JWT Bearer authentication for the REST API

- **Issue:** [#122](https://github.com/dawnrock/Proyect-DDD-Claude/issues/122)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-18
- **Status:** done
- **Total estimate:** ~85 min (9 tasks)

## Goal

Today every call to `/api/**` sends the username and password with HTTP Basic (ADR 0008). A client should send its credentials **once** to a public `POST /api/auth/token`, get a signed JWT that expires, and then call the API with `Authorization: Bearer <token>`. **HTTP Basic is removed from the API** (decided with the user on 2026-11-18): only Bearer is accepted. That is an incompatible change, so the contract becomes **2.0.0** (ADR 0014). The token is signed with **HS256** and a shared secret from `APP_JWT_SECRET`. The authorization rules stay the same: roles on the URLs and account ownership in `application` with the `Caller`.

### Out of scope

- The Thymeleaf web: it keeps the login form and the session (`WebSecurityConfig` unchanged).
- Refresh tokens, token revocation or a deny list, and logout on the API (stateless: short expiry instead).
- OAuth2/OIDC with an external identity provider, RS256 or key rotation.
- A limit on login attempts (already pending since #50).
- The `Monolithic` branch.

## Context

The resource server support of Spring Security validates the Bearer token (`oauth2ResourceServer().jwt()`). It needs `spring-boot-starter-security-oauth2-resource-server` (Spring Boot 4 name; it brings `spring-security-oauth2-jose` with Nimbus), a new dependency that needs an ADR. Spring's `NimbusJwtEncoder` issues the token and `NimbusJwtDecoder` checks it, both with the same `SecretKey` (HS256, at least 256 bits). Everything lives in `adapter.in.rest`. `application` still does not know Spring Security (ArchUnit `la_aplicacion_no_depende_de_spring_security`), and `CurrentCaller` keeps turning the `Authentication` into a `Caller`. It works the same with a `JwtAuthenticationToken` as long as the roles become `ROLE_*` authorities and the name is the `sub` claim.

**Token:** claims `iss=ddd-lab`, `sub=<username>`, `roles=["ADMIN"|"USER"]`, `iat`, `exp`. Default lifetime 15 min (`app.jwt.ttl`). Response `200 {"accessToken": "...", "tokenType": "Bearer", "expiresIn": 900}`.

**Secret:** comes from `app.jwt.secret` / `APP_JWT_SECRET`. The `demo` profile has a development secret (practice data, like the demo BCrypt hashes). Without a secret the application generates a random key at startup and logs a warning: it still starts, but tokens stop working after a restart. A secret shorter than 32 bytes is rejected at startup.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build | `pom.xml` | + `spring-boot-starter-security-oauth2-resource-server` |
| `adapter.in.rest` | `JwtSupport.java`, `JwtProperties.java` (new) | Secret key, `JwtEncoder`/`JwtDecoder` HS256, lifetime |
| `adapter.in.rest` | `AccessTokenIssuer.java`, `TokenController.java`, `TokenRequestDef`/`TokenResponseDef` (new, Immutables DTOs) | `POST /api/auth/token` |
| `adapter.in.rest` | `ApiSecurityConfig.java` | Basic → resource server JWT, public token endpoint, `WWW-Authenticate: Bearer` |
| `adapter.in` | `SecuritySupport.java` | `AuthenticationManager` bean (DAO provider over the existing users) |
| resources | `application.properties`, `application-demo.properties` | `app.jwt.ttl`, demo secret |
| tests | `DddLabApplicationTests`, `DemoProfileTests`, `CustomerControllerTests`, new `ApiSecurityTests`, `TokenControllerTests`, `JwtSupportTests` | Bearer instead of Basic |
| contract tests | `schemathesis.toml` | Dynamic Bearer auth instead of Basic |
| docs | `docs/adr/0015-jwt-bearer-api.md`, `openapi.yaml` 2.0.0, `CHANGELOG.md`, README, `render.md`, status files | — |

## Tasks

### [x] T1 — ADR 0015 and the dependency (~5 min)

- **Layer:** build · docs
- **Files:** `docs/adr/0015-jwt-bearer-api.md`, `docs/adr/README.md`, `docs/adr/0008-spring-security.md` (status: amended by 0015), `pom.xml`
- **Depends on:** —
- **What to do:**
  1. Write the ADR: context (Basic sends the password on every request), decision (resource server + Nimbus, HS256, Basic removed from `/api`, web unchanged, contract 2.0.0), alternatives (keep Basic alongside Bearer with a deprecation period; jjwt; a hand-made filter; RS256; external OIDC), consequences (no revocation, so a short lifetime; the secret is an environment variable).
  2. Add the starter to `pom.xml` with a comment pointing to the ADR.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green with the new dependency (nothing uses it yet).

### [x] T2 — Key, encoder and decoder (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/rest/JwtProperties.java`, `adapter/in/rest/JwtSupport.java`, `application.properties`, `application-demo.properties`, test `JwtSupportTests`
- **Depends on:** T1
- **What to do:**
  1. `JwtProperties` (`app.jwt.secret`, `app.jwt.ttl` = `PT15M`, `app.jwt.issuer` = `ddd-lab`).
  2. `JwtSupport`: a `SecretKey` HmacSHA256 from the secret (startup error if it is shorter than 32 bytes; a random key and a `log.warn` if it is missing), a `JwtEncoder` and a `JwtDecoder` (HS256, issuer and expiry checked).
  3. Demo secret in `application-demo.properties`.
- **Acceptance criteria:**
  - [x] A token encoded with the key decodes with its claims; one signed with another key, expired, or with another issuer is rejected.
  - [x] A short secret fails at startup with a clear message.

### [x] T3 — `POST /api/auth/token` (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/SecuritySupport.java` (`AuthenticationManager`), `adapter/in/rest/AccessTokenIssuer.java`, `adapter/in/rest/TokenController.java`, `TokenRequestDef`, `TokenResponseDef`, test `TokenControllerTests`
- **Depends on:** T2
- **What to do:**
  1. `AuthenticationManager` with a `DaoAuthenticationProvider` over the existing `UserDetailsManager` and `PasswordEncoder` (so signed-up customers can get a token too).
  2. `AccessTokenIssuer.issue(Authentication)` → claims `sub`, `roles`, `iat`, `exp`, `iss`.
  3. The controller validates the body (`@NotBlank`), authenticates and answers 200 with `accessToken`, `tokenType`, `expiresIn`. A `BadCredentialsException` gives 401 `ProblemDetail` "No autenticado" with the same body as today.
  - *Deviation:* `AccessTokenIssuer` builds its own `ProviderManager` instead of publishing an `AuthenticationManager` bean in `SecuritySupport`, so the web login keeps the manager it had. The `permitAll` of `/api/auth/token` moved here from T4, because the endpoint has to be reachable for its tests.
- **Acceptance criteria:**
  - [x] Valid credentials → 200 and a token whose `sub`/`roles` match; wrong password or unknown user → 401 with the same message; missing fields → 400.

### [x] T4 — The API chain accepts only Bearer (~10 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/rest/ApiSecurityConfig.java`, new test `ApiSecurityTests` (`@WebMvcTest` with real tokens; `src/test/CLAUDE.md` keeps the full context to `DddLabApplicationTests`)
- *Deviation:* T4 and T5 share one commit: once Basic is gone, the end-to-end tests cannot stay green until they use a token. `ApiSecurityConfig` imports `JwtSupport`, so the existing `@WebMvcTest`s did not change. The public sign-up test that sent wrong Basic credentials now sends an invalid Bearer token (still 401).
- **Depends on:** T3
- **What to do:**
  1. Replace `httpBasic` with `oauth2ResourceServer(jwt)` and a `JwtAuthenticationConverter` (claim `roles` → `ROLE_*`, name = `sub`).
  2. `POST /api/auth/token` and `POST /api/customers` public; the role rules unchanged.
  3. The entry point answers 401 `ProblemDetail` with `WWW-Authenticate: Bearer` (also for an invalid or expired token); the 403 stays as it is. Update the Javadoc.
- **Acceptance criteria:**
  - [x] A valid Bearer → 200 on `GET /api/accounts`. A `USER` opening an account → 403. Missing, expired or wrongly signed token → 401 `ProblemDetail`.
  - [x] An `Authorization: Basic ...` header → 401 (Basic is no longer accepted).
  - [x] `CurrentCaller` gives the same `Caller` (ownership tests unchanged); the `@WebMvcTest`s with `@WithMockUser` keep passing.

### [x] T5 — End-to-end tests with a token (~10 min)

- **Layer:** tests
- **Files:** `DddLabApplicationTests`, `DemoProfileTests`, `CustomerControllerTests`, helper `support/ApiTokens`
- **Depends on:** T4
- **What to do:**
  1. Replace the Basic credentials with: ask `/api/auth/token`, then send `Bearer`.
  2. Sign-up flow: a newly signed-up customer gets a token and sees their account.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green with no Basic left in the API tests (`grep -rn httpBasic src/test` empty for `/api`).

### [x] T6 — OpenAPI contract 2.0.0 (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`
- **Depends on:** T5
- **What to do:**
  1. `bearerAuth` (`http`, `bearer`, `bearerFormat: JWT`) replaces `basicAuth` as the global scheme.
  2. New operation `issueToken` (`POST /api/auth/token`, tag `auth`, `security: []`) with its schemas, named examples, 200/400/401/415.
  3. Update the 401 descriptions (no Bearer, invalid or expired token) and the 401 of `signUp`, which now only happens with an invalid Bearer token.
  4. `info.version: 2.0.0` and a CHANGELOG entry that lists the incompatible change. Run `redocly lint` and `oasdiff breaking` (it must report the change, which is why the version is a major one).
- **Acceptance criteria:**
  - [x] `npx @redocly/cli lint docs/api/openapi.yaml` with no errors; `scripts/check-contract-version.mjs` accepts the bump (`oasdiff` reports 7 incompatible changes, 1.0.0 → 2.0.0).

### [x] T7 — Contract tests with a token (~10 min)

- **Layer:** build
- **Files:** `schemathesis.toml` (no Python hooks: Schemathesis 4.33.4 fetches tokens declaratively)
- **Depends on:** T6
- **What to do:**
  1. Replace `[auth.basic]` with the OpenAPI-aware dynamic auth of the `bearerAuth` scheme:

     ```toml
     [auth.dynamic.openapi.bearerAuth]
     path = "/api/auth/token"
     payload = { username = "admin", password = "demo" }
     extract_selector = "/accessToken"
     ```

     The defaults are `method = "post"`, `payload_content_type = "application/json"`, `extract_from = "body"` and `retry_on = [401]`, so the token is fetched again after a 401. Operations with `security: []` (`issueToken`, `signUp`) get no token, because the auth follows the contract's schemes.
  2. Adjust the `expected-statuses` of `issueToken` if the checks need it (401 for random credentials).
  3. Fallback only if the dynamic auth falls short: a hooks file `scripts/schemathesis_hooks.py` with `@schemathesis.auth(refresh_interval=600).skip_for(operation_id=["issueToken", "signUp"])` (class `ApiTokenAuth` with `get`/`set`), loaded with `hooks = "scripts/schemathesis_hooks.py"` in `schemathesis.toml`.
- **Acceptance criteria:**
  - [x] `node scripts/contract-test.mjs` passes locally (694 cases; the CI runs it on the PR).
  - *Deviation:* the first run found that an undeclared method (`QUERY`) on the two public paths answered 401 instead of 405. Before, Schemathesis sent Basic on every request, so this was hidden. Both paths are now public for any method, so Spring MVC answers 405; there are tests for it in `TokenControllerTests` and `CustomerControllerTests`. No change of `expected-statuses` was needed.

### [x] T8 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `README.md`, `docs/deploy/render.md`, `docs/contracts/login-roles.md` (note pointing to ADR 0015), `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T7
- **What to do:**
  1. README: how to get a token with `curl` and use it; Swagger UI: call `issueToken`, then **Authorize** with the Bearer.
  2. `render.md`: the `APP_JWT_SECRET` variable (32 bytes or more, for example `openssl rand -base64 48`) on the Hexagonal service.
  3. Status files: decision (Bearer only on `/api`, HS256, 15 min), pending items (no revocation, no refresh, no attempt limit on the token endpoint), history row.
- **Acceptance criteria:**
  - [x] The README `curl` example works against `./mvnw spring-boot:run`.

### [x] T9 — Review fixes (~10 min)

- **Layer:** adapter.in · tests · docs
- **Files:** `RestExceptionHandler.java`, `JwtSupport.java`, `ApiSecurityTests`, `TokenControllerTests`, `openapi.yaml`, `render.md`, `decisiones.md`, `pendientes.md`
- **Depends on:** T8
- **What to do:** the `revisor-ddd` review (no blocking findings):
  1. The 401 of `POST /api/auth/token` sends `WWW-Authenticate: Bearer` (RFC 9110), documented in the contract.
  2. Tests for a token without signature (`alg: none`), one signed with HS512 and one without `roles` (no role: 200 as a user, 403 opening an account).
  3. Stale lines in `decisiones.md`; a warning in `render.md` that the demo secret is public; the fail-open secret noted in `pendientes.md`; style (static imports, line length, example token).
  4. Not changed: the body of ADR 0008 (an ADR records a past decision; its header already points to ADR 0015).
- **Acceptance criteria:**
  - [x] The new header test fails when the header is removed (sabotage) and passes with it; `./mvnw verify` green.

## Global acceptance criteria

**Specific:**

- [x] `POST /api/auth/token` with valid credentials returns a JWT (HS256, `sub`, `roles`, `exp`); with invalid ones, 401 `ProblemDetail`.
- [x] Every `/api/**` operation except `issueToken` and `signUp` needs `Authorization: Bearer`; Basic is rejected.
- [x] Authorization is unchanged: `ADMIN` on the URLs, ownership through `Caller` (someone else's account = 404).
- [x] No secret in the repository apart from the demo development one; production reads `APP_JWT_SECRET`.
- [x] The web login (form + session) works as before.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR): ADR 0015.
- [x] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [x] Any new business term is in `docs/dominio/glosario.md` (n/a: "access token" is technical, not a domain term).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Starter name in Spring Boot 4.1:** check that `spring-boot-starter-security-oauth2-resource-server` exists in the BOM. If it does not, use `spring-boot-starter-oauth2-resource-server`.
- **Render deployment:** without `APP_JWT_SECRET` the service starts with a random key, so tokens become invalid after every restart or sleep of the free plan. The variable must be set before the merge is deployed.
- **No revocation:** a stolen token is valid until it expires (15 min). This is accepted and recorded in `pendientes.md`.
- **The token endpoint has no limit on attempts**, like the web login: it is a brute-force target on the public deployment.
- **Schemathesis auth:** decided on 2026-11-18. Use `[auth.dynamic.openapi.bearerAuth]` in `schemathesis.toml` (checked against the 4.33.4 config classes), with the hooks file as a fallback (T7).
- **Lifetime:** confirmed with the user, 15 min (`app.jwt.ttl=PT15M`).
- **Secret generation:** `openssl rand -base64 48` (64 characters, 48 random bytes). The application uses the UTF-8 bytes of the value as the HMAC key (at least 32 bytes), so any long random string works. Documented in the README and `render.md` (T8).

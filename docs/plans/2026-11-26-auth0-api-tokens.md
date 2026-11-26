# Plan: The API accepts Auth0 access tokens and Swagger UI signs in with PKCE

- **Issue:** [#197](https://github.com/dawnrock/Proyect-DDD-Claude/issues/197)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-26
- **Status:** done
- **Total estimate:** ~100 min (10 tasks)

## Goal

The REST API accepts **access tokens issued by Auth0** (RS256, checked with Auth0's public keys), like a real bank with one identity provider for every channel, while it keeps accepting its own tokens from `POST /api/auth/token` (HS256) as long as local authentication is on: two issuers during the migration. The `sub` of an Auth0 token is translated into the local user with the `external_identity` link of #196, and the **roles come from our database**, so `CurrentCaller`, the role rules and account ownership do not change. Swagger UI gets an «Authorize» button that signs in with Auth0 (Authorization Code with PKCE). Without the new variables, the API behaves exactly as today.

Second of four issues: #196 (done) → **#197** → #198 sign-up through Auth0 → #199 realistic mode in production.

### Out of scope

- Turning the token endpoint off in `prod` (#199). Here `POST /api/auth/token` and the HS256 issuer only follow `app.auth.local`, which stays `true` in every profile.
- Linking an identity through the API: an Auth0 identity is linked once on the web (#196); the API answers 401 to an unlinked one and says so.
- Roles or permissions in Auth0 (Actions, RBAC claims, scopes per operation).
- Refresh tokens and token revocation.

## Context

**Two issuers.** `ApiSecurityConfig` replaces `oauth2ResourceServer().jwt()` with a `JwtIssuerAuthenticationManagerResolver` that maps each trusted `iss` to its own `AuthenticationManager`:

| Issuer | When | Decoder | Name and authorities |
| ------ | ---- | ------- | -------------------- |
| `ddd-lab` (own, `JwtSupport`) | `app.auth.local=true` (default) | HS256 with `APP_JWT_SECRET`, as today | `sub` and the `roles` claim, as today |
| `https://<domain>/` (Auth0) | `APP_AUTH_AUTH0_DOMAIN` **and** `APP_AUTH_AUTH0_AUDIENCE` set | RS256 from `https://<domain>/.well-known/jwks.json`; keys fetched on the first Auth0 token, never at startup | `sub` → `ExternalIdentities` → local username; authorities from `UserDetailsService` |

Any other `iss` is rejected by the resolver with 401 `invalid_token`, as is an expired token, a bad signature or an Auth0 token whose `aud` does not contain the audience. An Auth0 token of an **unlinked** `sub`, or of a missing or disabled local user, answers 401 `invalid_token` with its own detail («La identidad de Auth0 no está vinculada a ningún usuario: entra una vez en la web con Auth0 para vincularla»), so a developer knows what to do.

**Audience.** Auth0 only issues a JWT access token for an API when the authorization request asks for an `audience` (the API identifier created in the Auth0 dashboard). Checking `aud` is what stops a token issued for another API of the same tenant from being accepted here. New variable `APP_AUTH_AUTH0_AUDIENCE` (`app.auth.auth0.audience`): without it, the API does not trust Auth0 (the web login of #196 does not need it). An audience without a domain fails at startup.

**Swagger UI.** Swagger UI runs in the browser, so it is a **public client**: it needs its own Auth0 application of type *Single Page Application* (no secret, PKCE), separate from the Regular Web Application of the web login. New variable `APP_AUTH_AUTH0_API_DOCS_CLIENT_ID` (`app.auth.auth0.api-docs-client-id`), not secret.
- The contract stays tenant-agnostic: a new security scheme `auth0` of type `openIdConnect` with `openIdConnectUrl: /api-docs/openid-configuration`, an endpoint of ours that builds a minimal discovery document from the domain (issuer, authorization and token endpoints, `code_challenge_methods_supported: [S256]`), with no call to Auth0.
- `GET /api-docs/oauth-config` gives the page what it needs (`enabled`, `clientId`, `audience`); the page calls `ui.initOAuth({clientId, usePkceWithAuthorizationCodeGrant: true, additionalQueryStringParams: {audience}})` only when enabled.
- The OAuth redirect page must be on our origin: `oauth2-redirect.html` (and its script) from `swagger-ui-dist@5.34.1`, the version the page already pins, are copied into `static/api-docs/`.

**Own token endpoint.** `TokenController` and `AccessTokenIssuer` become conditional on `app.auth.local` (default `true`), as #196 agreed; with it off, `POST /api/auth/token` answers 404 and the own issuer is not in the resolver. No profile turns it off here.

**Contract.** Adding an alternative security scheme is backward compatible: `info.version` 2.2.4 → **2.3.0** (ADR 0014). Schemathesis keeps using `bearerAuth` with the own token (`schemathesis.toml` unchanged unless the new scheme confuses it).

**Not a team issue:** only `lado:back` (REST adapter, security support, the Swagger UI static page and the contract), so `/implement-plan`.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in` (support) | `Auth0Properties` | `audience`, `apiDocsClientId`; validation |
| `adapter.in.rest` | `Auth0AccessTokens` (new), `ApiSecurityConfig`, `JwtSupport`, `TokenController`, `AccessTokenIssuer` | Auth0 decoder and converter, issuer resolver, own issuer behind `app.auth.local` |
| `adapter.in.web` | `ApiDocsController` | `/api-docs/oauth-config`, `/api-docs/openid-configuration` |
| static | `static/api-docs/index.html`, `oauth2-redirect.html` (+ script) | «Authorize» with Auth0 and PKCE |
| docs | `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `docs/adr/0024-oauth2-oidc-auth0.md`, `docs/deploy/render.md`, `docs/ia/*` | Contract 2.3.0, decisions, tenant setup, status |
| tests | unit and `@WebMvcTest` with a local RSA key; `Auth0ApiTokensTests` (full context) | No network |

## Tasks

### [x] T1 — `Auth0Properties`: audience and Swagger UI client (~5 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/Auth0Properties.java`; test `Auth0PropertiesTests` (or the existing registration test)
- **Depends on:** —
- **What to do:**
  1. Add `audience` and `apiDocsClientId` (blank by default). `audience` or `apiDocsClientId` without the three web values → `IllegalStateException` naming the missing variables, like the partial configuration of #196. Helper `trustsApiTokens()` = domain and audience set.
- **Acceptance criteria:**
  - [x] Without them, everything as in #196; with the domain trio plus audience, `trustsApiTokens()`; audience alone fails at startup with the variable names.

### [x] T2 — `Auth0AccessTokens`: validators and `sub` → local user (~10 min)

- **Layer:** adapter.in.rest
- **Files:** `adapter/in/rest/Auth0AccessTokens.java`; test `Auth0AccessTokensTests`
- **Depends on:** T1
- **What to do:**
  1. A static `validator(issuer, audience)`: default timestamps + issuer + `aud` contains the audience.
  2. A converter `Jwt → AbstractAuthenticationToken`: `ExternalIdentities.usernameOf("auth0", sub)` → `UserDetailsService` → `JwtAuthenticationToken(jwt, localAuthorities, localUsername)`. Unlinked `sub`, or a missing or disabled user → `UnlinkedAccessTokenException`, an `invalid_token` of our own (RFC 6750 descriptions must be ASCII, so the Spanish text goes in the `ProblemDetail`; the user's state is never told apart).
- **Acceptance criteria:**
  - [x] A linked `sub` gives the local name and `ROLE_*`; unlinked, missing and disabled all give `UnlinkedAccessTokenException`; the validator rejects wrong `iss`, wrong `aud` and expired tokens.

### [x] T3 — Auth0 decoder, only with domain and audience (~10 min)

- **Layer:** adapter.in.rest
- **Files:** `adapter/in/rest/Auth0ApiTokensConfig.java` (`@Configuration` with its own condition; `Auth0Properties.baseUrl()`/`issuerUri()`), test `Auth0ApiTokensConfigTests` (`ApplicationContextRunner`)
- **Depends on:** T2
- **What to do:**
  1. Bean `auth0JwtDecoder`: `NimbusJwtDecoder.withJwkSetUri("https://<domain>/.well-known/jwks.json").jwsAlgorithm(RS256)` with T2's validator. Nimbus fetches the keys on the first decode, so startup stays offline.
  2. Test the validation with a decoder built from a local RSA key pair (same validator), signing tokens in the test with `NimbusJwtEncoder`.
- **Acceptance criteria:**
  - [x] No bean without domain or without audience; with both, the bean exists and the context starts with no network.
  - [x] A token signed with the test key, right `iss`/`aud`, decodes; wrong `aud` or `iss`, or expired, is rejected.

### [x] T4 — `ApiSecurityConfig`: one manager per issuer (~10 min)

- **Layer:** adapter.in.rest
- **Files:** `adapter/in/rest/ApiSecurityConfig.java`; tests `ApiAuth0TokensTests` (`@WebMvcTest` with a test RSA decoder for a fake tenant) and the existing API security tests
- **Depends on:** T3
- **What to do:**
  1. `oauth2ResourceServer(rs -> rs.authenticationManagerResolver(resolver))` with a `JwtIssuerAuthenticationManagerResolver` built from a map: own issuer → manager with the current HS256 decoder and roles converter (when `app.auth.local`); Auth0 issuer → manager with `auth0JwtDecoder` and T2's converter (when the bean exists).
  2. The entry point keeps its 401 `ProblemDetail` and `WWW-Authenticate: Bearer error="invalid_token"`, and uses the unlinked detail when the exception carries it.
- **Acceptance criteria:**
  - [x] A linked customer's Auth0 token lists only their accounts; an Auth0 token of an admin can `POST /api/accounts`; of a customer, 403.
  - [x] Unknown issuer, wrong audience, expired or unlinked → 401 `ProblemDetail` with `WWW-Authenticate: Bearer error="invalid_token"`; the unlinked one has its own detail.
  - [x] Own tokens keep working and every existing API security test passes unchanged.

### [x] T5 — Own token endpoint behind `app.auth.local` (~5 min)

- **Layer:** adapter.in.rest
- **Files:** `TokenController.java`, `AccessTokenIssuer.java`, `ApiSecurityConfig.java`; test `LocalTokensDisabledTests` (`@WebMvcTest` with `app.auth.local=false`)
- **Depends on:** T4
- **What to do:**
  1. `@ConditionalOnBooleanProperty(name = "app.auth.local", matchIfMissing = true)` on the controller and the issuer; the own issuer only joins the resolver when it is on.
- **Acceptance criteria:**
  - [x] With `app.auth.local=false`, `POST /api/auth/token` → 404 and an own token → 401; with the default, both work as today.

### [x] T6 — `/api-docs/oauth-config` and `/api-docs/openid-configuration` (~10 min)

- **Layer:** adapter.in.web
- **Files:** `adapter/in/web/ApiDocsController.java`; test `ApiDocsControllerTests`
- **Depends on:** T1
- **What to do:**
  1. `GET /api-docs/oauth-config` (JSON): `{"enabled": false}` unless domain, audience and `apiDocsClientId` are set; then `enabled`, `clientId`, `audience`. Public values only (never the web client's secret).
  2. `GET /api-docs/openid-configuration` (JSON): `issuer`, `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `response_types_supported: [code]`, `code_challenge_methods_supported: [S256]`, built from the domain; 404 when Auth0 is not configured. Both are public (already under `/api-docs/**`).
- **Acceptance criteria:**
  - [x] Without configuration: `enabled: false` and discovery 404; with it: the expected JSON, with no secret anywhere in either answer.

### [x] T7 — Swagger UI: «Authorize» with Auth0 and PKCE (~10 min)

- **Layer:** adapter.in.web (static)
- **Files:** `static/api-docs/index.html`, `static/api-docs/oauth2-redirect.html` (+ `oauth2-redirect.js` if 5.34.1 ships it apart), copied from `swagger-ui-dist@5.34.1` with a README line on where they come from; test in `ApiDocsControllerTests` (MockMvc over the static files)
- **Depends on:** T6
- **What to do:**
  1. The page fetches `/api-docs/oauth-config` before creating the UI, passes `oauth2RedirectUrl: <origin>/api-docs/oauth2-redirect.html`, and calls `initOAuth` with the client id, PKCE and `audience` only when enabled. Without Auth0 it behaves as today.
- **Acceptance criteria:**
  - [x] `/api-docs/oauth2-redirect.html` is served from our origin; the page has the `oauth2RedirectUrl`, `usePkceWithAuthorizationCodeGrant: true` and the `audience` parameter; without Auth0 nothing breaks (checked by hand once with `spring-boot:run`; the browser check of the Auth0 flow runs after T8, once the contract has the scheme).

### [x] T8 — Contract 2.3.0 (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/api/CHANGELOG.md`, `schemathesis.toml` only if needed
- **Depends on:** T4, T5, T6
- **What to do:**
  1. Scheme `auth0` (`type: openIdConnect`, `openIdConnectUrl: /api-docs/openid-configuration`) with a description: Auth0 access token for the API audience, the identity must be linked once on the web, roles from monA Bank. Global `security` becomes the alternative `bearerAuth` **or** `auth0`; the public operations stay `security: []`. The 401 description mentions the unlinked case. `POST /api/auth/token` says it exists only while local authentication is on.
  2. `info.version` 2.3.0, CHANGELOG entry, `npx @redocly/cli lint`, `oasdiff breaking` with no breaking change, `node scripts/contract-test.mjs` green.
- **Acceptance criteria:**
  - [x] Lint without errors, no breaking change, contract version check and Schemathesis green.
  - [x] Browser check (Chromium, fake tenant, Swagger UI files served from the same npm package as jsDelivr): Swagger UI reads the relative `openIdConnectUrl`, offers «authorization_code with PKCE» with the public client id, and its Auth0 redirect carries `code_challenge_method=S256`, the audience and our `oauth2-redirect.html`.

### [x] T9 — End-to-end: an Auth0 token against the real context (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/dawnrock/ddd/Auth0ApiTokensTests.java` (full context, fake tenant, `@TestBean` replacing `auth0JwtDecoder` with one from a local RSA key)
- **Depends on:** T4, T5
- **What to do:**
  1. Link `ada`'s `sub` in `external_identity`, sign an access token for the API audience with the test key, `GET /api/accounts` lists only her accounts and `GET` of another customer's account is 404; an admin's token opens an account; an unlinked `sub` is 401 with its detail; an own token still works.
- **Acceptance criteria:**
  - [x] Passes without network; validated by sabotage (dropping the audience check makes the wrong-audience case pass).

### [x] T10 — Docs and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0024-oauth2-oidc-auth0.md` (API section), `docs/adr/0015-jwt-bearer-api.md` (status line), `docs/deploy/render.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T9
- **What to do:**
  1. ADR 0024: two issuers, audience, roles from the database, Swagger UI as a public SPA client with PKCE, tenant-agnostic contract through our discovery endpoint.
  2. `render.md`: in Auth0, create the **API** (identifier = audience, RS256) and a **Single Page Application** for Swagger UI (Allowed Callback URLs `https://proyect-ddd-claude.onrender.com/api-docs/oauth2-redirect.html`, Allowed Web Origins `https://proyect-ddd-claude.onrender.com`, access to the API); in Render, `APP_AUTH_AUTH0_AUDIENCE` and `APP_AUTH_AUTH0_API_DOCS_CLIENT_ID`; manual check through Swagger UI.
  3. Status files: #197 done, next step #198.
- **Acceptance criteria:**
  - [x] Docs in English; CI's status check passes.

### [x] T11 — Review fixes (~10 min)

- **Layer:** adapter.in.rest · adapter.in.web · docs
- **Files:** `Auth0UnavailableException` (new), `ApiSecurityConfig`, `JwtSupport`, `TokenController`, `ApiDocsController`; tests `ApiAuth0UnavailableTests`, `ApiIssuersTests`, `ApiAuth0TokensTests`; `openapi.yaml` and CHANGELOG (still 2.3.0)
- **Depends on:** T10
- **What to do:**
  1. `revisor-ddd` findings: Auth0's keys unreachable answered 500 (Spring rethrows `AuthenticationServiceException`) → the Auth0 decoder translates any non-`BadJwtException` into `Auth0UnavailableException` and the entry point answers 503 `ProblemDetail`; two managers for the same `iss` fail at startup (`trust`) instead of overwriting; HS256/RS256 confusion cases added to the rejected tokens; the discovery document advertises only `openid` like the page and the contract; the own decoder is `@Primary`; imports and long lines tidied; the plan's file names follow the code.
- **Acceptance criteria:**
  - [x] An Auth0 token with Auth0 unreachable gets 503 without `WWW-Authenticate` (seen red first: the exception escaped the chain).
  - [x] An HS256 token signed with the own secret claiming Auth0's issuer, and an RS256 token of the tenant claiming the own issuer, both get 401 (characterization: each issuer already accepted only its algorithm and key).
  - [x] A repeated issuer fails with `IllegalStateException` (seen red first: the method did not exist).

## Global acceptance criteria

**Specific:**

- [x] Without `APP_AUTH_AUTH0_AUDIENCE` the API behaves as today (own tokens only) and nothing contacts Auth0 at startup.
- [x] An Auth0 access token for the API audience, of a linked identity, gets the local user's accounts and role; an unlinked one gets 401 with a detail that says how to link it.
- [x] Wrong audience, unknown issuer, bad signature or expired → 401 `ProblemDetail` with `WWW-Authenticate: Bearer error="invalid_token"`.
- [x] With `app.auth.local=false`, `POST /api/auth/token` is 404 and own tokens are rejected (no profile sets it yet).
- [x] Swagger UI offers «Authorize» with Auth0 and PKCE when configured, and works as today when not.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR): none expected (resource server and OAuth2 client are already in).
- [x] If the API changes: `docs/api/openapi.yaml` reflects it with `info.version` bumped per ADR 0014 and an entry in `docs/api/CHANGELOG.md`, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [x] Any new business term is in `docs/dominio/glosario.md`: n/a (security terms, in ADR 0024).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Two more things to create in Auth0** (the user's tenant): the API (its identifier is the audience) and a Single Page Application for Swagger UI. Without them the code is done but the Auth0 path of the API cannot be tried on Render; the CI never needs them.
- **Swagger UI and `openIdConnect` (resolved in T8).** Swagger UI 5.34.1 resolves the relative `openIdConnectUrl` against the page and offers the PKCE flow; checked in Chromium. The fallback was not needed.
- **Schemathesis and the alternative scheme.** It should keep authenticating through `bearerAuth`; if it warns about or tries `auth0`, the fix goes in `schemathesis.toml` as a reviewed change.
- **JWKS on the first Auth0 token.** The first request with an Auth0 token pays one call to `jwks.json` (cached afterwards by Nimbus). If Auth0 is unreachable, Spring would rethrow the error (500); since the review (T11) that request gets 503 `ProblemDetail` without asking for another token. The own tokens never depend on it.
- **`sub` of the API token = `sub` of the ID token.** Auth0 uses the same user id for every application of the tenant, so the link made on the web works for the API; the end-to-end test assumes it, the manual check on Render confirms it.

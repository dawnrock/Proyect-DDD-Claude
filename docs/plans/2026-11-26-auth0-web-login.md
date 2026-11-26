# Plan: Web login with Auth0 (OIDC) and identity linking

- **Issue:** [#196](https://github.com/dawnrock/Proyect-DDD-Claude/issues/196)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-26
- **Status:** in progress
- **Total estimate:** ~110 min (13 tasks)

## Goal

The web lets a customer **sign in with Auth0** (OpenID Connect, Authorization Code with PKCE, confidential client: tokens stay in the server session) next to the current login form. The Auth0 identity (`sub`) is translated into the **local username**, so `CurrentCaller`, the `Caller`, account ownership in `application` and the ArchUnit rules do not change. The first time an unknown identity arrives, the customer **links** it once with their local username and password. Logging out also ends the Auth0 session. Without Auth0 configured, the application behaves exactly as today.

First of four issues: #196 (this one) → #197 API accepts Auth0 tokens → #198 sign-up through Auth0 → #199 realistic mode in production.

### Out of scope

- The API accepting Auth0 tokens and the Swagger UI login (#197).
- Signing up a new customer with an Auth0 identity (#198): an unknown identity can only be linked here.
- Turning local authentication off in `prod` and failing at startup without Auth0 (#199). This plan only creates the `app.auth.local` switch, `true` in every profile.
- Account linking initiated from a signed-in session ("link my Auth0 account" in a profile page) and unlinking.

## Context

**Where identity lives.** Credentials and identities never enter `application` (ADR 0008, `CustomerRegistration`): users and authorities are Spring Security tables managed from the security support in the root of `adapter.in` (`UserStore`, `JdbcUserDetailsManager`). The external identity is the same kind of data, so it lives there too: `ExternalIdentities` is an interface in the root of `adapter.in` with a JDBC implementation and an in-memory test double, both passing one abstract contract (`ExternalIdentitiesContract`), as the persistence ports do. This deviates from the issue text ("persistence port"): an application port would make `application` know a security concept, which ADR 0008 forbids. `theAdapterInSupportDoesNotKnowTheAdapters` keeps the support free of `web`/`rest`.

**OIDC flow.**

1. `/login?form` shows **"Entrar con Auth0"** (only when Auth0 is configured) → `GET /oauth2/authorization/auth0` → 302 to `https://<domain>/authorize` with `state`, `nonce` and PKCE (`code_challenge_method=S256`).
2. Auth0 calls back `/login/oauth2/code/auth0`; Spring validates the ID token and calls our `OidcUserService`.
3. `LinkedOidcUserService` loads the OIDC user, looks up `(auth0, sub)` in `ExternalIdentities`:
   - **Linked** → returns an `OidcUser` whose `getName()` is the local username and whose authorities are the local `ROLE_*` (from `UserDetailsService`, so the database decides roles). A disabled local user is rejected.
   - **Unknown** → throws `UnlinkedIdentityException` (an `OAuth2AuthenticationException`) carrying a `PendingIdentity(provider, subject, email)`.
4. The failure handler stores the `PendingIdentity` in the HTTP session (with its creation instant; valid 10 minutes) and redirects to `/login/link`. Other OAuth failures go to `/login?oauth-error`.
5. `/login/link` asks for the local username and password once. `ExternalIdentityLinking.link(...)` checks them with the same `ThrottledAuthenticationProvider` as the login form (ADR 0017: failures count, blocks apply), stores the link, and the controller removes the pending identity and redirects to `/oauth2/authorization/auth0` again. Auth0 still has its session, so the second round trip is silent and now finds the link. **No programmatic authentication**: the only way into a session stays Spring Security's own flow (session fixation protection, `SecurityContextRepository`).

**Configuration.** Our own `Auth0Properties` (`app.auth.auth0.domain`, `client-id`, `client-secret`; on Render through relaxed binding, `APP_AUTH_AUTH0_DOMAIN`, `APP_AUTH_AUTH0_CLIENT_ID`, `APP_AUTH_AUTH0_CLIENT_SECRET`). No placeholders with empty defaults in `application.properties`: an empty value would count as present. The `ClientRegistration` is **built by hand from the domain** (Auth0's endpoints are fixed: `/authorize`, `/oauth/token`, `/userinfo`, `/.well-known/jwks.json`, issuer `https://<domain>/`, `end_session_endpoint` `https://<domain>/oidc/logout`), so startup makes no network call and tests need no network. All three blank → no registration, the web is as today. Only some of them → startup fails with a clear message.

**Logout.** A session that came from Auth0 (`OAuth2AuthenticationToken`) logs out through `OidcClientInitiatedLogoutSuccessHandler` (`id_token_hint` + `post_logout_redirect_uri={baseUrl}/login?logout`); a local session keeps `/login?logout`. The tenant needs "RP-Initiated Logout End Session Endpoint Discovery" enabled and the URL in "Allowed Logout URLs".

**htmx.** The pages have `hx-boost="true"` on `body`. A boosted link to `/oauth2/authorization/auth0` or a boosted logout POST would follow the redirect to Auth0 with XHR and fail on CORS, so the Auth0 link and the logout form get `hx-boost="false"`.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| build · docs | `pom.xml`, `docs/adr/0024-oauth2-oidc-auth0.md`, `docs/adr/README.md`, ADR 0008 and 0015 status lines | `spring-boot-starter-security-oauth2-client`, justified in ADR 0024 |
| db | `src/main/resources/db/migration/V3__external_identity.sql` | `external_identity(provider, subject, username)`: PK `(provider, subject)`, unique `(provider, username)`, FK to `users` |
| `adapter.in` (support) | `ExternalIdentities`, `JdbcExternalIdentities`, `PendingIdentity`, `IdentityAlreadyLinkedException`, `UnlinkedIdentityException`, `LinkedOidcUserService`, `ExternalIdentityLinking`, `Auth0Properties`, `Auth0ClientRegistration`, `AuthProperties` | New: storage, OIDC user resolution, linking, client registration, `app.auth.local` |
| `adapter.in` (support) | `UserStore`, `SecuritySupport` | Expose `ExternalIdentities` (JDBC) and the new properties |
| `adapter.in.web` | `WebSecurityConfig`, `LoginController`, `IdentityLinkController`, `IdentityLinkForm` | `oauth2Login()` when configured, failure and logout handlers, `/login/link` public, form login behind `app.auth.local` |
| templates | `login.html`, `login-link.html`, `fragments/layout.html` | "Entrar con Auth0" button, link page, `hx-boost="false"` on logout |
| tests | `ExternalIdentitiesContract`, `JdbcExternalIdentitiesTests`, `InMemoryExternalIdentities` (+ test), `TestUsers`/web test imports | Contract, doubles, `oidcLogin()` |
| docs | `docs/contracts/auth0-web-login.md`, `docs/deploy/render.md`, `docs/ia/*` | Contract, Auth0 tenant setup, status |

## Tasks

### [x] T0 — Contract (~10 min)

- **Side:** both
- **Layer:** adapter.in · docs
- **Files:** `docs/contracts/auth0-web-login.md`; types without logic in the root of `adapter.in`: `ExternalIdentities`, `PendingIdentity`, `IdentityAlreadyLinkedException`, `ExternalIdentityLinking` (signature only)
- **Depends on:** —
- **What to do:**
  1. Write the contract from `implement-plan-team/assets/contract-template.md`, adapted: there is **no application service** (identity stays in `adapter.in`, ADR 0008). The "service" section is the shared support API:
     - `ExternalIdentities`: `Optional<String> usernameOf(String provider, String subject)`, `void link(String provider, String subject, String username)` (throws `IdentityAlreadyLinkedException` if the username already has an identity of that provider; linking the same pair twice is a no-op).
     - `PendingIdentity(String provider, String subject, String email, Instant createdAt)` with `isExpired(Clock)` (10 minutes) and the session attribute name `PendingIdentity.SESSION_ATTRIBUTE`.
     - `ExternalIdentityLinking.link(PendingIdentity pending, String username, String rawPassword, String clientIp)`: returns the local username; throws `BadCredentialsException`, `TooManyLoginAttemptsException`, `IdentityAlreadyLinkedException`.
  2. Errors table: what the link page shows for each exception and for an expired or missing pending identity (back to `/login?form` with "Vuelve a entrar con Auth0").
  3. Screens: `/login?form` (button when `auth0Enabled`), `GET/POST /login/link`, `/login?oauth-error`, `/login?logout`; routes owned by Spring (`/oauth2/authorization/auth0`, `/login/oauth2/code/auth0`).
  4. Security wiring: who owns which file (back: support classes, V3, ADR; web: `WebSecurityConfig`, controllers, templates).
  5. No REST change: the OpenAPI section says "n/a (#197)".
- **Acceptance criteria:**
  - [x] Contract agreed by backend-dev and web-dev and frozen in a commit; `./mvnw verify` compiles against the types.

### [x] T1 — ADR 0024 and the OAuth2 client dependency (~10 min)

- **Side:** back
- **Layer:** build · docs
- **Files:** `pom.xml`, `docs/adr/0024-oauth2-oidc-auth0.md`, `docs/adr/README.md`, `docs/adr/0008-spring-security.md`, `docs/adr/0015-jwt-bearer-api.md`
- **Depends on:** T0
- **What to do:**
  1. ADR 0024: context (ADR 0008 and 0015 named OAuth2/OIDC as the end state; ADR 0019 made users durable), decision (Auth0 as identity provider for the web, roles from our database, `external_identity` link, link with the local password, RP-initiated logout, hand-built registration, `app.auth.local`; the four issues), alternatives (Keycloak self-hosted, roles in an Auth0 claim, linking by e-mail, a separate branch or module for the realistic mode), consequences (tenant setup, secrets on Render, Auth0 outage blocks only Auth0 logins).
  2. Add `spring-boot-starter-security-oauth2-client` with a comment pointing to ADR 0024. Mark ADR 0008 and 0015 as "amended by ADR 0024".
- **Acceptance criteria:**
  - [x] `./mvnw verify` green with the new dependency and no other change in behaviour.

### [x] T2 — Flyway V3: `external_identity` (~5 min)

- **Side:** back
- **Layer:** persistence (schema)
- **Files:** `src/main/resources/db/migration/V3__external_identity.sql`
- **Depends on:** T0
- **What to do:**
  1. `provider varchar(20)`, `subject varchar(255)`, `username varchar(50)`; PK `(provider, subject)`, unique `(provider, username)`, FK `username` → `users(username)`. Plain SQL valid for H2 (PostgreSQL mode) and PostgreSQL.
- **Acceptance criteria:**
  - [x] `DddLabApplicationTests` starts (Flyway applies V3 and `ddl-auto=validate` passes).

### [x] T3 — `ExternalIdentities`: contract, JDBC and in-memory (~10 min)

- **Side:** back
- **Layer:** adapter.in (support)
- **Files:** `adapter/in/JdbcExternalIdentities.java`, `UserStore.java` (bean); tests `adapter/in/ExternalIdentitiesContract.java`, `JdbcExternalIdentitiesTests.java`, `InMemoryExternalIdentities.java` (+ `InMemoryExternalIdentitiesTests.java`)
- **Depends on:** T2
- **What to do:**
  1. Contract: an unknown pair gives empty; after `link` the pair gives the username; linking the same pair twice is a no-op; a second identity of the same provider for the same username → `IdentityAlreadyLinkedException`; the same subject cannot point to two usernames.
  2. `JdbcExternalIdentities` with `JdbcClient`, translating the unique violation into `IdentityAlreadyLinkedException`; bean next to `userDetailsService` in `UserStore` (depends on database initialisation). The JDBC test runs on H2 with a user created first (FK).
- **Acceptance criteria:**
  - [x] Both implementations pass `ExternalIdentitiesContract`.

### [x] T4 — Auth0 client registration from three properties (~10 min)

- **Side:** back
- **Layer:** adapter.in (support)
- **Files:** `adapter/in/Auth0Properties.java`, `adapter/in/Auth0ClientRegistration.java`, `SecuritySupport.java` (enable properties); test `Auth0ClientRegistrationTests.java`
- **Depends on:** T1
- **What to do:**
  1. `Auth0Properties(domain, clientId, clientSecret)`, blank by default; `isConfigured()`; partial configuration → `IllegalStateException` naming the missing variables.
  2. A `ClientRegistrationRepository` bean only when configured (custom condition on non-blank values): registration id `auth0`, `client_secret_basic`, `authorization_code`, redirect `{baseUrl}/login/oauth2/code/{registrationId}`, scopes `openid profile email`, the Auth0 endpoints and issuer from the domain, `end_session_endpoint` in the provider metadata, user name attribute `sub`.
- **Acceptance criteria:**
  - [x] Without properties there is no `ClientRegistrationRepository`; with the three, the registration has the expected endpoints, scopes and `end_session_endpoint`; with only some, startup fails with the message.

### [x] T5 — `LinkedOidcUserService`: `sub` → local user (~10 min)

- **Side:** back
- **Layer:** adapter.in (support)
- **Files:** `adapter/in/LinkedOidcUserService.java`, `adapter/in/UnlinkedIdentityException.java`; test `LinkedOidcUserServiceTests.java`
- **Depends on:** T3
- **What to do:**
  1. Wraps the default `OidcUserService` (delegate injected, mocked in the test as Spring's `OAuth2UserService` interface). Linked → an `OidcUser` that keeps the ID token and claims but answers `getName()` with the local username and has the local authorities (`UserDetailsService`). Unknown → `UnlinkedIdentityException` with the `PendingIdentity` (`sub`, `email`, now). Local user disabled or missing → `OAuth2AuthenticationException` (`invalid_user`).
- **Acceptance criteria:**
  - [x] Linked identity → name = local username and `ROLE_USER`/`ROLE_ADMIN` from the store; unknown → `UnlinkedIdentityException` with subject and e-mail; disabled user → rejected.

### [x] T6 — `ExternalIdentityLinking`: link with the local password (~10 min)

- **Side:** back
- **Layer:** adapter.in (support)
- **Files:** `adapter/in/ExternalIdentityLinking.java`; test `ExternalIdentityLinkingTests.java`
- **Depends on:** T3
- **What to do:**
  1. Authenticates `username`/`rawPassword` with the `ThrottledAuthenticationProvider` (IP in `WebAuthenticationDetails`, so the attempt limit applies and the username is normalised, #157), then `identities.link(pending.provider(), pending.subject(), username)`. An expired pending identity → `IllegalStateException`. Uses `TestUsers` and `InMemoryExternalIdentities` in the test.
- **Acceptance criteria:**
  - [x] Right password → linked and the normalised username returned; wrong password → `BadCredentialsException` and it counts for the limit; after the limit → `TooManyLoginAttemptsException` even with the right password; username already linked to another identity → `IdentityAlreadyLinkedException`.

### [x] T7 — Login page: "Entrar con Auth0" (~5 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `adapter/in/web/LoginController.java`, `templates/login.html`; test `LoginControllerTests` (or the existing login web test)
- **Depends on:** T0
- **What to do:**
  1. `auth0Enabled` in the model from `ObjectProvider<ClientRegistrationRepository>`. On the form page, below "Entrar", a separator and an `<a class="btn-slant btn-slant-surface" hx-boost="false" href="/oauth2/authorization/auth0">Entrar con Auth0</a>`. `?oauth-error` shows the form with "No se pudo iniciar sesión con Auth0. Inténtalo de nuevo." and `?link-expired` with "Vuelve a entrar con Auth0 para vincular tu usuario".
- **Acceptance criteria:**
  - [x] With Auth0 configured, the form shows the button with `hx-boost="false"`; without it, the page is unchanged; both messages show on their parameter.

### [x] T8 — Link page `/login/link` (~10 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `adapter/in/web/IdentityLinkController.java`, `adapter/in/web/IdentityLinkForm.java`, `templates/login-link.html`; test `IdentityLinkControllerTests.java`
- **Depends on:** T0
- **What to do:**
  1. `GET`: no pending identity in the session or expired → `/login?link-expired`; otherwise the form with the Auth0 e-mail ("Has entrado en Auth0 como …. Para terminar, escribe una sola vez tu usuario y tu contraseña de monA Bank"), username (lower-cased like `SignUpForm`) and password with its show-password button, brand style of the login form.
  2. `POST` (CSRF): calls `ExternalIdentityLinking` (mocked from the contract) with the client IP (`ClientIpResolver`); success → removes the pending identity and redirects to `/oauth2/authorization/auth0`; `BadCredentialsException` → "Usuario o contraseña incorrectos"; `TooManyLoginAttemptsException` → the blocking message of the login; `IdentityAlreadyLinkedException` → "Ese usuario ya está vinculado a otra cuenta de Auth0". Accessible errors as in #145.
- **Acceptance criteria:**
  - [x] Without a pending identity → redirect to `/login?link-expired`; with one, the success POST redirects to `/oauth2/authorization/auth0` and clears the session attribute; each error shows its message with status 200 and the form.

### [x] T9 — Web chain: `oauth2Login()` when configured (~10 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `adapter/in/web/WebSecurityConfig.java`; test `WebSecurityConfigOAuth2Tests.java` (`@WebMvcTest` with a test `ClientRegistrationRepository` for a fake domain)
- **Depends on:** T0
- **What to do:**
  1. If a `ClientRegistrationRepository` is available: `oauth2Login()` with `loginPage("/login")`, `userInfoEndpoint().oidcUserService(...)`, PKCE on the authorization request resolver (`OAuth2AuthorizationRequestCustomizers.withPkce()`, unless Spring Security already applies it by default to confidential clients), failure handler: `UnlinkedIdentityException` → store the `PendingIdentity` in the session and redirect to `/login/link`; anything else → `/login?oauth-error`. `/login/link` is public.
- **Acceptance criteria:**
  - [x] `GET /oauth2/authorization/auth0` → 302 to `https://<fake-domain>/authorize` with `code_challenge_method=S256`, `state` and `nonce`; without a registration the route does not exist and the existing web tests still pass.
  - [x] The failure handler with an `UnlinkedIdentityException` leaves the `PendingIdentity` in the session and redirects to `/login/link`.

### [x] T10 — Logout in Auth0 and the `app.auth.local` switch (~10 min)

- **Side:** web
- **Layer:** adapter.in.web
- **Files:** `adapter/in/web/WebSecurityConfig.java`, `adapter/in/AuthProperties.java`, `templates/fragments/layout.html`; tests in `WebSecurityConfigOAuth2Tests.java`
- **Depends on:** T9
- **What to do:**
  1. Logout success handler: `OAuth2AuthenticationToken` → `OidcClientInitiatedLogoutSuccessHandler` with `post_logout_redirect_uri={baseUrl}/login?logout`; otherwise `/login?logout`. Logout form with `hx-boost="false"`.
  2. `AuthProperties(boolean local)` (`app.auth.local`, default `true`): `formLogin()` only when `true`. No profile sets it to `false` in this issue (#199 does).
- **Acceptance criteria:**
  - [x] Logout with `oidcLogin()` → 302 to `https://<fake-domain>/oidc/logout` with `id_token_hint` and `post_logout_redirect_uri`; logout of a form session → `/login?logout`.
  - [x] With `app.auth.local=false`, `POST /login` with valid credentials does not authenticate; with the default it does.

### [x] T11 — End-to-end: link and sign in (~10 min)

- **Side:** both
- **Layer:** tests
- **Files:** `src/test/java/com/dawnrock/ddd/Auth0WebLoginTests.java` (full context with `app.auth.auth0.*` for a fake domain)
- **Depends on:** T3–T10
- **What to do:**
  1. With the real beans and H2: `/login?form` shows the button; `GET /oauth2/authorization/auth0` redirects with PKCE; a session holding a `PendingIdentity` + `POST /login/link` with a demo user's credentials stores the link in `external_identity` and redirects to `/oauth2/authorization/auth0`; `LinkedOidcUserService` then resolves that `sub` to the demo user (called with a stub delegate) and `oidcLogin()` with that name and role sees "Mis cuentas" with only their accounts.
  2. Without the properties (`DddLabApplicationTests`) nothing changes.
- **Acceptance criteria:**
  - [x] The test passes without network; `./mvnw verify` green.

### [ ] T12 — Docs and project status (~10 min)

- **Side:** both
- **Layer:** docs
- **Files:** `docs/deploy/render.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T11
- **What to do:**
  1. `render.md`: Auth0 tenant setup (Regular Web Application; Allowed Callback URLs `https://<service>.onrender.com/login/oauth2/code/auth0`; Allowed Logout URLs `https://<service>.onrender.com/login?logout`; RP-Initiated Logout End Session Endpoint Discovery on) and the three `APP_AUTH_AUTH0_*` variables (the secret only in Render, never in the repository).
  2. Status files: #196 done, next step #197; decision (identity in `adapter.in`, link with password, re-entry instead of programmatic login); pending items (no unlinking, no linking from a signed-in session, pending identity only in the session).
- **Acceptance criteria:**
  - [ ] Docs in English; CI's status check passes.

### [x] T13 — Review fixes (~10 min)

- **Side:** both
- **Layer:** adapter.in · adapter.in.web · docs
- **Files:** `PendingIdentityExpiredException` (new), `ExternalIdentityLinking`, `IdentityLinkController`, `Auth0Properties`; their tests; ADR 0024 and the contract
- **Depends on:** T11
- **What to do:**
  1. `revisor-ddd` findings: the link page treats every `AuthenticationException` as a wrong password (a disabled account answered 500 and revealed its state); an expired pending identity throws its own exception instead of `IllegalStateException`; `Auth0Properties.isConfigured()` was unused; the ADR says the link page is the only password check left once #199 turns the form login off, and how the 10 minutes are enforced.
- **Acceptance criteria:**
  - [x] A disabled local account on the link page gets «Usuario o contraseña incorrectos» (200), seen red first.
  - [x] An expired pending identity throws `PendingIdentityExpiredException` and the page redirects to `/login?link-expired`, seen red first.

## Global acceptance criteria

**Specific:**

- [ ] Without `APP_AUTH_AUTH0_*` the application starts and the web behaves as today (all existing tests green, no network at startup).
- [ ] An Auth0 identity that is already linked signs in with the local username and local role and only sees their accounts.
- [ ] An unknown identity goes to the link page; the right password links it and signs in after the silent re-entry; a wrong one does not and counts as a failed login attempt (ADR 0017).
- [ ] An identity cannot be linked to two usernames, nor a username to two identities of the same provider.
- [ ] Logging out of an Auth0 session ends it in Auth0 too and lands on `/login?logout`.
- [ ] The authorization request uses PKCE (S256), and the Auth0 link and the logout form are not boosted by htmx.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR): `spring-boot-starter-security-oauth2-client`, ADR 0024.
- [ ] If the API changes: n/a (the API does not change until #197).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (external identity and linking are security terms, not domain language; they go to ADR 0024).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Not tested against a real tenant in CI.** Everything runs with a fake domain and `oidcLogin()`. The first real check is on Render once the user creates the Auth0 application and sets the three variables; a manual checklist goes in `render.md`.
- **PKCE default.** If Spring Security 7 already sends PKCE for confidential clients, the customizer is dropped and the test still asserts S256.
- **Pending identity in the session.** It lives at most 10 minutes and only holds what Auth0 already verified (`sub`, e-mail). The session id changes on the final login (Spring's session fixation protection).
- **Auth0 e-mail is not used to link.** Linking by e-mail would be easier, but the local users have no e-mail and an unverified e-mail would let anyone take an account; the password proves ownership.
- **The demo users on Render** (`admin`, customers) can link their own Auth0 identities; the repository holds no Auth0 data.

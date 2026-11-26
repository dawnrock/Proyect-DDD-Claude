# 0024 · Web login with Auth0 (OAuth2/OIDC) and identity linking

- **Status:** accepted · 2026-11-26
- **Issue:** #196 (first of four: #196 web login, #197 the API accepts Auth0 tokens, #198 sign-up through Auth0, #199 realistic mode in production) · **Amends:** [ADR 0008](0008-spring-security.md) (the web authenticates with Auth0 as well as with the form) and [ADR 0015](0015-jwt-bearer-api.md) (OAuth2/OIDC was named there as the end state) · **Related:** [ADR 0019](0019-real-persistence.md) (users in the database), [ADR 0017](0017-login-attempt-limit.md) (login attempts), [ADR 0007](0007-equipo-back-web-contrato-primero.md) (contract first)

## Context

ADR 0008 and ADR 0015 left OAuth2/OIDC as the end state of the authentication: the application keeps its own users and passwords only as a first step. ADR 0019 made those users durable (Spring Security's `users` / `authorities` tables in PostgreSQL), which makes it possible to link an external identity to them. The aim is a login that looks like a real bank's: the customer signs in at an identity provider and the application never sees a new password.

## Decision

- **Auth0 is the identity provider of the web** (OpenID Connect, Authorization Code with PKCE). The application is a confidential client: the tokens stay in the server session, the browser only holds the session cookie. The form login stays next to it.
- **Roles come from our database**, not from a claim of the ID token. An Auth0 session has the **local username** as its name and the local `ROLE_*` as its authorities, so `CurrentCaller`, the `Caller` and the account ownership in `application` do not change.
- **Identity lives in the root of `adapter.in`**, next to `UserStore` and `CustomerRegistration`, never in `application` (ADR 0008): `ExternalIdentities` (JDBC implementation and an in-memory test double passing the same contract), `LinkedOidcUserService`, `ExternalIdentityLinking`, `Auth0Properties`, `Auth0ClientRegistration`. This deviates from the issue text ("persistence port"): an application port would make `application` know a security concept. No input port, command or view changes.
- **Table `external_identity`** (Flyway `V3`): primary key `(provider, subject)`, unique `(provider, username)` and a foreign key to `users(username)`. An identity cannot point to two users, and a user has at most one identity per provider. **The foreign key prevents deleting a linked user** until the link is removed (there is no unlinking yet).
- **The link is proved with the local password.** The first time an unknown `sub` arrives, `LinkedOidcUserService` throws `UnlinkedIdentityException`; the web chain stores a `PendingIdentity` (provider, `sub`, e-mail, instant; valid 10 minutes) in the HTTP session and redirects to `/login/link`, where the customer writes their local username and password once. `ExternalIdentityLinking` checks them with the same `ThrottledAuthenticationProvider` as the login form (ADR 0017: failures count, blocks apply). A disabled, locked or expired local account gets the same notice as a wrong password, so the page does not reveal an account's state. The e-mail is only shown, never used to link.
- **No programmatic authentication after linking.** The controller removes the pending identity and redirects to `/oauth2/authorization/auth0` again: Auth0 still has its session, so the round trip is silent and now finds the link. The only way into a session stays Spring Security's own flow (session fixation protection, `SecurityContextRepository`).
- **The `ClientRegistration` is built by hand from the domain** (`Auth0ClientRegistration`): Auth0's endpoints are fixed (`/authorize`, `/oauth/token`, `/userinfo`, `/.well-known/jwks.json`, issuer `https://<domain>/`, `end_session_endpoint` `https://<domain>/oidc/logout`), so startup makes no network call and tests need none. The beans exist only when `app.auth.auth0.domain`, `client-id` and `client-secret` are all non-blank (own `Condition`: `@ConditionalOnProperty` takes `""` as present). `Auth0Properties` is always bound; with only some of them set, startup fails naming the missing variables (`APP_AUTH_AUTH0_DOMAIN`, `APP_AUTH_AUTH0_CLIENT_ID`, `APP_AUTH_AUTH0_CLIENT_SECRET`). With none, the web is as before.
- **PKCE is declared on the registration** (`ClientSettings.requireProofKey(true)`). Spring Security 7.1.1 already defaults to it for every client, confidential ones included; declaring it keeps PKCE if that default ever changes, and a test checks `code_challenge_method=S256`.
- **RP-initiated logout:** a session that came from Auth0 logs out through `OidcClientInitiatedLogoutSuccessHandler` (`id_token_hint` and `post_logout_redirect_uri={baseUrl}/login?logout`); a local session keeps `/login?logout`. htmx must not boost the Auth0 link nor the logout form (`hx-boost="false"`): following a redirect to another origin with XHR fails on CORS.
- **`app.auth.local`** (`AuthProperties`, default `true`) switches the form login off. No profile sets it to `false` here; #199 does. With it off, the link page is on purpose the **only** place where a local password is still checked: an existing customer must prove once that the Auth0 identity is theirs (same attempt limit as the login).
- **Dependency:** `spring-boot-starter-security-oauth2-client` (the resource server of ADR 0015 was already there). Spring's own `OidcUserService`, state, nonce and ID token validation are reused; nothing is written by hand.

## Alternatives

- **Keycloak self-hosted:** full control and no external service, but one more thing to run and pay for beside two free Render services and Neon; Auth0's free plan is enough for a practice project.
- **Roles in a claim of Auth0** (an Action adding `roles` to the token): one source for identity and authorisation, but the roles would live outside the repository and its tests, and the API would depend on the tenant's configuration. The database already decides roles.
- **Linking by e-mail:** easier for the customer, but the local users have no e-mail and an unverified e-mail would let anyone take over an account. The password proves ownership.
- **Programmatic login after linking** (building the `Authentication` in the controller): saves a round trip, but skips Spring's own flow and its session protections.
- **A separate branch or module for the realistic mode:** two code bases to keep in step. The mode is a configuration switch (`app.auth.*`) in the same code, which #199 will turn on in production.
- **`ClientRegistrations.fromIssuerLocation`:** reads the discovery document from Auth0, so startup and every test would need the network.

## Consequences

- The Auth0 tenant must be set up by hand (Regular Web Application, Allowed Callback and Logout URLs, RP-Initiated Logout End Session Endpoint Discovery) and its three values go to Render as `APP_AUTH_AUTH0_*`; the secret only there, never in the repository (`docs/deploy/render.md`).
- An Auth0 outage blocks only the Auth0 logins; the form login keeps working while `app.auth.local` is `true`.
- Nothing is tested against a real tenant in the CI: tests use a fake domain and `oidcLogin()`. The first real check is on Render.
- The pending identity lives only in the session and is honoured for 10 minutes: an expired one is dropped when it is read (it may stay in an idle session until that session ends, holding only `sub` and e-mail). There is no unlinking and no linking from a signed-in session; the foreign key means a linked user cannot be deleted before that exists.
- The users of the demo (`admin`, customers) can link their own Auth0 identities; the repository holds no Auth0 data.

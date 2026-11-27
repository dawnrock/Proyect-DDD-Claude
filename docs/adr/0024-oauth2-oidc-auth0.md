# 0024 · Web login with Auth0 (OAuth2/OIDC) and identity linking

- **Status:** accepted · 2026-11-26
- **Issues:** #196, #197, #198 and #199 (#196 web login, #197 the API accepts Auth0 tokens, #198 sign-up through Auth0, #199 realistic mode in production) · **Amends:** [ADR 0008](0008-spring-security.md) (the web authenticates with Auth0 as well as with the form) and [ADR 0015](0015-jwt-bearer-api.md) (OAuth2/OIDC was named there as the end state) · **Related:** [ADR 0019](0019-real-persistence.md) (users in the database), [ADR 0017](0017-login-attempt-limit.md) (login attempts), [ADR 0007](0007-equipo-back-web-contrato-primero.md) (contract first)

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
- **`app.auth.local`** (`AuthProperties`, default `true`) switches the form login off. The `prod` profile sets it to `false` since #199 (see «The realistic mode» below). With it off, the link page is on purpose the **only** place where a local password is still checked: an existing customer must prove once that the Auth0 identity is theirs (same attempt limit as the login).
- **Dependency:** `spring-boot-starter-security-oauth2-client` (the resource server of ADR 0015 was already there). Spring's own `OidcUserService`, state, nonce and ID token validation are reused; nothing is written by hand.

### The API (#197)

- **Two issuers on `/api/**`.** `ApiSecurityConfig` sends each Bearer token to the `AuthenticationManager` of its `iss` (`JwtIssuerAuthenticationManagerResolver`): the API's own tokens (`ddd-lab`, HS256, ADR 0015) while `app.auth.local` is on, and Auth0 access tokens (`https://<domain>/`, RS256) when `APP_AUTH_AUTH0_AUDIENCE` is set. Any other issuer is an invalid token (401). Each issuer has its own decoder, so an HS256 token can never be checked with the RS256 keys or the other way round.
- **Audience.** Auth0 only issues a JWT access token for an API when the authorization request names its `audience` (the identifier of the API created in the tenant). The decoder requires it in `aud` (`JwtAudienceValidator`), so a token issued for another API of the same tenant is rejected. Without the audience the API does not trust Auth0 at all; the web login does not need it.
- **Keys on demand.** The Auth0 decoder reads `https://<domain>/.well-known/jwks.json` on the first Auth0 token and caches it (Nimbus): startup never calls Auth0, and if Auth0 is unreachable that request gets 401, never 500. Own tokens never depend on it.
- **Same identity, same roles.** The token's `sub` is the same user id as the web's ID token, so the `external_identity` link of the web works for the API; `Auth0AccessTokens` turns it into a `JwtAuthenticationToken` named after the local user with the local `ROLE_*`. An unlinked `sub`, or a missing or disabled local user, gets the same 401 (`UnlinkedAccessTokenException`, an RFC 6750 `invalid_token` whose description stays ASCII as the header requires) with a Spanish `detail` saying how to link it. Linking stays on the web.
- **Own tokens follow `app.auth.local`.** `TokenController` and `AccessTokenIssuer` only exist, and the own issuer is only trusted, while it is on (default). #199 turns it off in production.
- **Swagger UI is a public client.** It runs in the browser and cannot keep a secret, so it is a separate Auth0 *Single Page Application* (`APP_AUTH_AUTH0_API_DOCS_CLIENT_ID`, not secret) that signs in with Authorization Code and PKCE for the API audience. The page asks `/api-docs/oauth-config` for that client and the audience; Auth0 redirects back to `oauth2-redirect.html`, copied unchanged from `swagger-ui-dist@5.34.1` into our origin.
- **Tenant-agnostic contract.** The `auth0` security scheme is `openIdConnect` with `openIdConnectUrl: /api-docs/openid-configuration`, a minimal discovery document this application builds from the domain (only `code` with S256). The contract file names no tenant and stays the same in every environment. Contract 2.3.0 (a compatible addition, ADR 0014).

### Sign-up through Auth0 (#198)

- **New customers need no local password.** An unknown Auth0 identity reaches `/login/link`, which offers both ways in: link an existing user with the local password (#196), or **sign up**. The sign-up is the same three-step wizard with the same rules (ADR 0012), in an *Auth0 mode* that asks for the username but no password and prefills the e-mail from Auth0 (editable and only informative).
- **The user still exists locally,** because it is the key of account ownership and of the roles: role `USER` and an **unusable password** (BCrypt of 32 random bytes that are never kept), so neither the form login nor `POST /api/auth/token` accept any password for it. `CustomerRegistration.registerWithExternalIdentity` reuses the classic sign-up (limit, probing counter, username checks, the use case) and then links the identity; `application` does not change.
- **The pending identity is honoured while the customer is active:** each completed step renews it (a sliding 10-minute window), every step checks it, and the browser cannot switch the wizard's mode. After the sign-up the web goes back to Auth0, as after linking: no programmatic sign-in, so no welcome message survives the round trip.
- The classic sign-up with a password stays while `app.auth.local` is on; #199 turns it off in production.

### The realistic mode (#199)

One code base, two modes chosen by `app.auth.local`:

| Piece | `true` (`demo`, `default`, tests, Schemathesis) | `false` (`prod`, Render) |
| --- | --- | --- |
| `POST /api/auth/token`, HS256 own tokens | Issued and accepted | No bean (404); an own token, even well signed, gets 401 |
| Tokens the API accepts | Own and, if configured, Auth0 | Only Auth0 (with `aud`) |
| Web login with a password | Form (plus Auth0 if configured) | Off: only «Entrar con Auth0»; both doors of `/login` go to Auth0 |
| Sign-up with a password (web classic wizard, `POST /api/customers`) | Available | Off: every step of the classic wizard goes to Auth0, `POST /api/customers` is 404, and `CustomerRegistration.register` refuses as a second barrier |
| Missing-token 401 `detail` | Asks for `POST /api/auth/token` | Asks for an Auth0 access token |
| Auth0 configuration | Optional (all or nothing, as above) | **Required, all five** `APP_AUTH_AUTH0_*` (`RealisticModeCheck`): any missing → startup fails naming them |

- **The local password is checked once,** on `/login/link`, so that customers who existed before #198 can prove an Auth0 identity is theirs. Administrators link the same way and then enter with Auth0, with the role from our database.
- **Emergency switch:** `APP_AUTH_LOCAL=true` in Render brings the local mode back without deploying code (the environment variable wins over `application-prod.properties`).
- **Failed Auth0 returns are logged** (`WARN` with the OAuth2 error code, never the description, tokens or personal data), so a failure in production can be diagnosed (#199, found while turning the mode on: the first sign-in after a redeploy failed once with no trace).

## Alternatives

- **Keycloak self-hosted:** full control and no external service, but one more thing to run and pay for beside two free Render services and Neon; Auth0's free plan is enough for a practice project.
- **Roles in a claim of Auth0** (an Action adding `roles` to the token): one source for identity and authorisation, but the roles would live outside the repository and its tests, and the API would depend on the tenant's configuration. The database already decides roles.
- **Linking by e-mail:** easier for the customer, but the local users have no e-mail and an unverified e-mail would let anyone take over an account. The password proves ownership.
- **Programmatic login after linking** (building the `Authentication` in the controller): saves a round trip, but skips Spring's own flow and its session protections.
- **A separate branch or module for the realistic mode:** two code bases to keep in step. The mode is a configuration switch (`app.auth.*`) in the same code, which #199 turns on in production.
- **`ClientRegistrations.fromIssuerLocation`:** reads the discovery document from Auth0, so startup and every test would need the network.
- **The API trusting only Auth0 at once:** realistic, but the tests, Schemathesis and the demo get their tokens from `POST /api/auth/token`; both issuers live together in the local mode, and only Auth0 in production (#199).
- **The realistic mode starting with only the web values of Auth0** (no audience): it would start, but the API would accept no token at all in production. The user chose to require all five (2026-11-27).
- **Swagger UI with the web's confidential client:** its secret would have to reach the browser. A public SPA client with PKCE is the standard for browser apps.
- **The tenant's URLs written in the contract (`oauth2` scheme):** simpler for Swagger UI, but the contract would change with the tenant and name the user's tenant in the repository.

## Consequences

- The Auth0 tenant must be set up by hand (Regular Web Application, Allowed Callback and Logout URLs, RP-Initiated Logout End Session Endpoint Discovery) and its three values go to Render as `APP_AUTH_AUTH0_*`; the secret only there, never in the repository (`docs/deploy/render.md`).
- An Auth0 outage blocks only the Auth0 logins while `app.auth.local` is `true`; in production (realistic mode, #199) it blocks every sign-in, and `APP_AUTH_LOCAL=true` is the way back to the form login.
- For the API (#197) the tenant also needs an **API** (its identifier is `APP_AUTH_AUTH0_AUDIENCE`) and a **Single Page Application** for Swagger UI (`APP_AUTH_AUTH0_API_DOCS_CLIENT_ID`; callback `…/api-docs/oauth2-redirect.html`, web origin the service URL). The CI never needs them: the tests sign tokens with a local RSA key.
- Nothing is tested against a real tenant in the CI: tests use a fake domain and `oidcLogin()`. The first real check is on Render.
- The pending identity lives only in the session and is honoured for 10 minutes: an expired one is dropped when it is read (it may stay in an idle session until that session ends, holding only `sub` and e-mail). There is no unlinking and no linking from a signed-in session; the foreign key means a linked user cannot be deleted before that exists.
- The users of the demo (`admin`, customers) can link their own Auth0 identities; the repository holds no Auth0 data.

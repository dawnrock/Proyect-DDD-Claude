# Contract: Web login with Auth0 (OIDC) and identity linking

- **Issue:** [#196](https://github.com/dawnrock/Proyect-DDD-Claude/issues/196) · **Plan:** `docs/plans/2026-11-26-auth0-web-login.md`
- **Status:** frozen at `33b4bd1`
- **Agreed by:** backend-dev, web-dev (rounds: 1; both accepted the signatures and proposed precisions, merged by the lead, since the teammates cannot message each other)

Builds on the frozen contracts `login-roles.md` (roles, `Caller`, security wiring) and `customer-sign-up.md` (users in the database, `ClientIpResolver`). ADR 0024 (written in T1) records the decisions.

## No application service

Identity and credentials never enter `application` (ADR 0008): users and authorities are Spring Security tables managed from the security support in the **root of `adapter.in`** (`UserStore`, `CustomerRegistration`). The external identity is the same kind of data, so this feature changes no input port, command or view. `CurrentCaller` and the `Caller` are unchanged: an Auth0 session has the **local username** as its name and the **local `ROLE_*`** as its authorities.

The shared API lives in the root of `adapter.in` (types created in the contract commit, bodies `throw new UnsupportedOperationException("pendiente: backend-dev")`):

```java
public interface ExternalIdentities {
    String AUTH0 = "auth0";                                            // provider id = OAuth2 registrationId
    Optional<String> usernameOf(String provider, String subject);      // local user linked to that sub, if any
    void link(String provider, String subject, String username);       // same pair again: no-op; IdentityAlreadyLinkedException if the username
}                                                                      // has another identity of the provider or the subject has another user;
                                                                       // IllegalArgumentException if the local user does not exist

public record PendingIdentity(String provider, String subject, String email, Instant createdAt) implements Serializable {   // no validation: email may be null
    String SESSION_ATTRIBUTE;                                          // HTTP session attribute holding it
    Duration VALIDITY;                                                 // 10 minutes
    boolean isExpired(Clock clock);
}

public class LinkedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    OidcUser loadUser(OidcUserRequest request);                        // linked sub -> OidcUser (DefaultOidcUser subclass) whose getName() is the local
}                                                                      // username, with the local roles, keeping the ID token (id_token_hint) and the
                                                                       // Auth0 claims (sub stays Auth0's); unknown sub -> UnlinkedIdentityException;
                                                                       // local user missing or disabled -> OAuth2AuthenticationException (invalid_user)

@Component
public class ExternalIdentityLinking {
    String link(PendingIdentity pending, String username, String rawPassword, String clientIp);  // checks the local password once, links; returns the normalised username
}

public class UnlinkedIdentityException extends OAuth2AuthenticationException { PendingIdentity pending(); }
public class IdentityAlreadyLinkedException extends RuntimeException { }

@ConfigurationProperties("app.auth")
public record AuthProperties(@DefaultValue("true") boolean local) { }   // enabled by SecuritySupport; complete in the contract commit
```

### Beans

| Bean | Who declares it | When |
| ---- | --------------- | ---- |
| `ExternalIdentities` (JDBC) | back, `UserStore` | always (needs the database, like `UserDetailsManager`) |
| `ExternalIdentityLinking` | back (`@Component`) | always; its constructor (provider, identities, `ObjectProvider<Clock>`) is back's choice. Only scanned in the full context: `@WebMvcTest`s mock it |
| `ClientRegistrationRepository` (registration `auth0`) | back, `Auth0ClientRegistration` (own `Condition`: the three values non-blank; not `@ConditionalOnProperty`, which takes `""` as present) | only when `app.auth.auth0.domain`, `client-id` and `client-secret` are all non-blank; some but not all → startup fails from `Auth0Properties` (always bound, enabled by `SecuritySupport`) naming the missing variables |
| `LinkedOidcUserService` | back, `Auth0ClientRegistration` | same condition |
| `AuthProperties` | contract commit, `SecuritySupport` | always |
| `Clock`, `ClientIpResolver` | `config.DomainConfiguration`, `SecuritySupport` | always in the full context; web reads the clock with `ObjectProvider<Clock>` (fallback `Clock.systemUTC()`), like `SecuritySupport` |

Web reads the optional beans with `ObjectProvider`, so every `@WebMvcTest` that imports `WebSecurityConfig` keeps working without them. The web tests that need Auth0 declare their own `ClientRegistrationRepository` for a fake domain (`https://tenant.example.auth0.com/`, endpoints below) and mock `LinkedOidcUserService` / `ExternalIdentityLinking` with `@MockitoBean`.

### Registration `auth0` (built by hand from the domain, no network at startup)

| Field | Value |
| ----- | ----- |
| registration id | `auth0` (`ExternalIdentities.AUTH0`) |
| client authentication / grant | `client_secret_basic` / `authorization_code` |
| redirect URI | `{baseUrl}/login/oauth2/code/{registrationId}` |
| scopes | `openid`, `profile`, `email` |
| authorization / token / user info / JWK set | `https://<domain>/authorize`, `/oauth/token`, `/userinfo`, `/.well-known/jwks.json` |
| issuer | `https://<domain>/` |
| provider metadata `end_session_endpoint` | `https://<domain>/oidc/logout` |
| user name attribute | `sub` |

## Errors

| Exception / case | When | Web (what the user sees) | REST |
| ---------------- | ---- | ------------------------ | ---- |
| `UnlinkedIdentityException` | Auth0 identity not linked | the failure handler stores `pending()` in the session (`PendingIdentity.SESSION_ATTRIBUTE`) and redirects to `/login/link` | n/a |
| other `AuthenticationException` from the OAuth2 login (state, token, `invalid_user` for a missing or disabled local user…) | Auth0 callback fails | redirect to `/login?oauth-error`: form with «No se pudo iniciar sesión con Auth0. Inténtalo de nuevo.» | n/a |
| no pending identity, or `isExpired` | `GET`/`POST /login/link` | redirect to `/login?link-expired`: form with «Vuelve a entrar con Auth0 para vincular tu usuario.» | n/a |
| `BadCredentialsException` | wrong username or password on the link page | link page again (200) with «Usuario o contraseña incorrectos» | n/a |
| `TooManyLoginAttemptsException` | user + IP or IP blocked (ADR 0017) | link page again (200) with the login's blocking message (minutes from `LoginAttemptsProperties`) | n/a |
| `IdentityAlreadyLinkedException` | the user already has another Auth0 identity | link page again (200) with «Ese usuario ya está vinculado a otra cuenta de Auth0» | n/a |

## REST (fragment of `docs/api/openapi.yaml`)

n/a: the API does not change (#197 adds Auth0 tokens).

## Screens (web)

| Path | Uses | Shows |
| ---- | ---- | ----- |
| `GET /login?form` | `ObjectProvider<ClientRegistrationRepository>` → model `auth0Enabled`; `AuthProperties` → model `localEnabled` | the current form (only when `localEnabled`) and, when `auth0Enabled`, a separator and `<a class="btn-slant btn-slant-surface" hx-boost="false" href="/oauth2/authorization/auth0">Entrar con Auth0</a>`. With `localEnabled=false` and Auth0, only the button, and the «Ya soy cliente» door goes to `/oauth2/authorization/auth0` (`hx-boost="false"`) |
| `GET /login?oauth-error`, `?link-expired` | — | the form page (`showForm`) with the message of the errors table (`role="alert"` and icon, like the other notices) |
| `GET /oauth2/authorization/auth0` | Spring Security | 302 to `https://<domain>/authorize` with `state`, `nonce`, `code_challenge` and `code_challenge_method=S256`. PKCE comes from the registration (`ClientSettings.requireProofKey(true)`, see Changes) |
| `GET /login/oauth2/code/auth0` | Spring Security + `LinkedOidcUserService` | linked → session and redirect to `/`; unlinked → `/login/link` |
| `GET /login/link` | session `PendingIdentity` | «Has entrado en Auth0 como <email>. Para terminar, escribe una sola vez tu usuario y tu contraseña de monA Bank.» (without the e-mail when it is `null`), username and password (show-password button), «Vincular», back link to `/login` |
| `POST /login/link` (CSRF, form with `hx-boost="false"`: its success ends on Auth0's domain) | `ExternalIdentityLinking.link(pending, username, password, clientIp)` with the IP of `ClientIpResolver` | success → removes the session attribute and redirects to `/oauth2/authorization/auth0` (Auth0 still has its session, so the round trip is silent and now finds the link); errors as in the table |
| `POST /logout` | Spring Security | `OAuth2AuthenticationToken` → `OidcClientInitiatedLogoutSuccessHandler`, `post_logout_redirect_uri={baseUrl}/login?logout`; any other session → `/login?logout`. The header's logout form gets `hx-boost="false"` |

`oauth2Login()` is configured only when a `ClientRegistrationRepository` is available, with `loginPage("/login")`, `defaultSuccessUrl("/", true)` and `userInfoEndpoint(u -> u.oidcUserService(linkedOidcUserService))`; its failure handler looks for `UnlinkedIdentityException` along the `getCause()` chain. The form login keeps its own `loginFailure()` handler. `/oauth2/authorization/**` and `/login/oauth2/code/**` are handled by `oauth2Login()` and need no `permitAll`.

The username of the link form is stripped and lower-cased like `SignUpForm` (#152); `ExternalIdentityLinking` normalises it anyway (#157). `/login/link` is public in the web chain. The form login (`formLogin()`) is only configured when `AuthProperties.local()` is `true` (default).

## Security wiring (who owns which file)

| File | Owner | Content |
| ---- | ----- | ------- |
| `pom.xml` (`spring-boot-starter-security-oauth2-client`), `AuthProperties`, `SecuritySupport` enabling it, the contract types | lead (T0) | this contract |
| `docs/adr/0024-oauth2-oidc-auth0.md`, ADR index and status lines of 0008/0015 | **back** (T1) | the decision |
| `db/migration/V3__external_identity.sql` | **back** (T2) | PK `(provider, subject)`, unique `(provider, username)`, FK to `users` |
| `JdbcExternalIdentities`, `UserStore`, `ExternalIdentitiesContract`, `InMemoryExternalIdentities` (test) | **back** (T3) | storage |
| `Auth0Properties`, `Auth0ClientRegistration` | **back** (T4) | registration, `LinkedOidcUserService` bean |
| `LinkedOidcUserService`, `PendingIdentity.isExpired` | **back** (T5) | `sub` → local user |
| `ExternalIdentityLinking` | **back** (T6) | linking |
| `LoginController`, `login.html` | **web** (T7) | Auth0 button and messages |
| `IdentityLinkController`, `IdentityLinkForm`, `login-link.html` | **web** (T8) | link page |
| `WebSecurityConfig`, `fragments/layout.html` | **web** (T9, T10) | `oauth2Login()` with PKCE when a `ClientRegistrationRepository` exists, failure handler, logout handler, `formLogin()` behind `AuthProperties.local()` |
| `Auth0WebLoginTests`, docs and status | lead (T11, T12) | integration |

## Changes

<!-- Each renegotiation: date, who asked, what changed and why. -->

- 2026-11-26, lead, integration (T11): PKCE **is** the default for confidential clients in Spring Security 7.1.1: `DefaultOAuth2AuthorizationRequestResolver` applies it when the method is `NONE` **or** `ClientSettings.isRequireProofKey()`, which defaults to `true` (the review only saw the first branch; found when the end-to-end test stayed green with the customizer removed). The custom resolver is dropped and `Auth0ClientRegistration` declares `requireProofKey(true)`, so PKCE does not depend on that default; the S256 tests stay.
- 2026-11-26, review round 1 (lead merged both reviews; no disagreement):
  - PKCE is explicit in `WebSecurityConfig` (both: not the default for `client_secret_basic` in 7.1.1).
  - `ExternalIdentities.link` throws `IllegalArgumentException` for a missing local user (backend-dev); `PendingIdentity` validates nothing; the Auth0 `OidcUser` is a `DefaultOidcUser` subclass that overrides `getName()` (backend-dev).
  - Own `Condition` for the Auth0 beans and `Auth0Properties` enabled by `SecuritySupport` (backend-dev).
  - `localEnabled` in the login model and the form hidden when local authentication is off (web-dev); `hx-boost="false"` also on the link form (web-dev); `?oauth-error` and `?link-expired` show the form page (web-dev); `Clock` through `ObjectProvider` (both).
- 2026-11-26, lead, before the review: the OAuth2 client dependency, `AuthProperties` and the skeletons of `LinkedOidcUserService` and `UnlinkedIdentityException` go in the contract commit (the plan had the dependency in T1 and `AuthProperties` in T10), so both sides compile against them in parallel.

# 0015 · JWT Bearer tokens for the REST API

- **Status:** accepted · 2026-11-18
- **Issue:** #122 · **Amends:** [ADR 0008](0008-spring-security.md) (authentication of `/api/**`) · **Related:** [ADR 0014](0014-contract-versioning.md) (contract 2.0.0)

## Context

Since #50 the REST API authenticates every request with HTTP Basic: the client sends its username and password on every call. That keeps the password travelling all the time, and every request pays a BCrypt check. Professional APIs hand out a short-lived token instead, and the client sends only that token. HTTP Basic and Bearer only live together while consumers migrate. This API has no external consumers besides the tests, Schemathesis and Swagger UI.

## Decision

- **Token endpoint:** a public `POST /api/auth/token` receives `{"username", "password"}` as JSON, checks them against the existing users (the same `UserDetailsManager` and BCrypt as the web) and answers `{"accessToken", "tokenType": "Bearer", "expiresIn"}`. Wrong credentials give 401 `ProblemDetail`.
- **Token:** a JWT signed with **HS256** and a shared secret. The claims are `iss=ddd-lab`, `sub=<username>`, `roles`, `iat` and `exp`. It lasts **15 minutes** (`app.jwt.ttl`).
- **Secret:** comes from `APP_JWT_SECRET` (`app.jwt.secret`), and must be at least 32 bytes. The `demo` profile has a development secret, which is practice data like the demo BCrypt hashes. Without a secret the application generates a random key at startup and logs a warning, so tokens stop working after a restart.
- **Validation:** Spring Security's resource server (`oauth2ResourceServer().jwt()`) with `NimbusJwtDecoder`. The `roles` claim becomes `ROLE_*` authorities and `sub` becomes the name, so `CurrentCaller`, the role rules on the URLs and account ownership in `application` stay as they are.
- **HTTP Basic is removed from `/api/**`**: only `Authorization: Bearer` is accepted. This is an incompatible change, so the contract goes to **2.0.0**.
- **The web does not change:** form login, session and CSRF (`WebSecurityConfig`).
- **Dependency:** `spring-boot-starter-security-oauth2-resource-server`, which brings `spring-security-oauth2-jose` and Nimbus JOSE + JWT. Everything lives in `adapter.in.rest`. `application` still does not know Spring Security.

## Alternatives

- **Keep Basic next to Bearer, deprecated, and remove it later:** this is the usual migration path when a contract has consumers. It doubles the attack surface and the tests, and this API has nobody to migrate.
- **jjwt or java-jwt by hand with a custom filter:** another dependency anyway, and it would reimplement what the resource server already does (bearer token parsing, 401 with `WWW-Authenticate`, the authentication object).
- **RS256 with a key pair:** only worth it when other services must validate the token with the public key. Here a single service signs and validates.
- **OAuth2/OIDC with an external identity provider (Keycloak, Auth0):** the professional end state, but it needs infrastructure that a local practice project does not have. ADR 0008 already discarded it.

## Consequences

- **No revocation:** a stolen token is valid until it expires. The short lifetime limits the damage. Changing `APP_JWT_SECRET` invalidates every token at once. There is no refresh token: when the token expires, the client asks for a new one.
- `APP_JWT_SECRET` must be set on every deployment (on Render, before deploying this change).
- Clients and tests make two calls: get the token, then use it. Schemathesis fetches it with its dynamic auth (`schemathesis.toml`).
- The token endpoint has no limit on attempts, like the web login (pending since #50).

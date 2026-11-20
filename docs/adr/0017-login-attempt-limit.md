# 0017 · Limit on login attempts

- **Status:** accepted · 2026-11-20
- **Issue:** #130 · **Amends:** [ADR 0008](0008-spring-security.md) (web login) and [ADR 0015](0015-jwt-bearer-api.md) (`POST /api/auth/token`) · **Related:** [ADR 0014](0014-contract-versioning.md) (contract 2.1.0)

## Context

The application is public on Render and the demo user names (`admin`, `ada`, `grace`, `alan`) are in the repository. Nothing limited the login attempts: neither the web form (`/login`, pending since #50) nor the API's token endpoint (`POST /api/auth/token`, pending since #122). Anyone could try passwords against the administrator without limit.

## Decision

- **Policy (OWASP), chosen by the user:**
  - 3 failures for a *user from one IP* block that pair.
  - 10 failures from *one IP with any user* block that IP.
  - Both locks last 30 minutes from the last failure.
  - Failures older than that window stop counting, and a successful login clears the failures of its pair.
  - While a lock lasts, even the right password is refused.
  - An attacker on another IP cannot lock the real user out.
- **Configurable:** `app.security.login-attempts.max-per-user`, `max-per-ip` and `lockout` (defaults 3, 10 and 30m).
- **Where:** a `ThrottledAuthenticationProvider` wraps the user-and-password provider in the shared security support of `adapter.in`. The web form login and the API's `AccessTokenIssuer` both use it, and both pass the request's IP in the authentication details (`WebAuthenticationDetails`). The counters live in `LoginAttempts`, in memory, with the application's `Clock`.
- **Answers:**
  - **Web:** `/login?blocked` with "Demasiados intentos fallidos. Vuelve a intentarlo dentro de 30 minutos."
  - **API:** 429 `ProblemDetail` with `Retry-After` in seconds.
  - **No user enumeration:** an unknown user is counted and answered exactly like a known one.
- **Client IP behind Render's proxy:** `server.forward-headers-strategy=native`. Tomcat takes the last address of `X-Forwarded-For` that is not an internal proxy (10/8, 172.16/12, 192.168/16, 100.64/10, loopback…). Whatever a client adds to the header itself stays to the left and does not count, and without a proxy in front the header is ignored.
- **Lock warnings:** when a lock starts, a warning goes to the log with the user (line breaks removed) and the IP.
- **Contract tests:** they start the application with very high thresholds. Schemathesis sends invented credentials and would otherwise lock its own IP.

## Alternatives

- **A rate-limiting library (Bucket4j, Resilience4j):** a new dependency for a counter that takes a few dozen lines. It fits better when there are several limits or shared storage (Redis).
- **A limit in a reverse proxy or a WAF:** the usual place in production, but Render's free plan has none that can be configured.
- **Per user only:** stops attacks spread over many IPs, but anyone can lock the administrator out by failing on purpose.
- **Per IP only:** does not protect one user from an attack spread over many IPs.
- **A servlet filter:** it would have to read the API's JSON body to know the user. The provider already receives the user and the IP for both entry points.

## Consequences

- **Counters in memory:** they reset on restart and are not shared across instances. Render runs one instance; several instances would need shared storage.
- **Bounded memory:** expired entries are pruned on access and every 1,000 failures, and each key keeps at most its last *max* failures.
- **Shared IPs:** an office or a carrier NAT is blocked as a whole after 10 failures, for 30 minutes. This is accepted, and the threshold is configurable.
- **Render's proxy address:** if Render's proxy reached the container from an address outside the internal ranges, Tomcat would not trust `X-Forwarded-For`. Every request would then share the proxy's IP, and 10 failures would block everyone. The lock warning in the log shows the counted IP, so the first deploy has to be checked; `server.tomcat.remoteip.internal-proxies` fixes it.
- **No notification:** there is no CAPTCHA, no notification to the user and no permanent lock.

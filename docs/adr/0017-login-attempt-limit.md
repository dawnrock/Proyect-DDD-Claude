# 0017 · Limit on login attempts

- **Status:** accepted · 2026-11-20
- **Issues:** #130, #133 (client IP behind Cloudflare) · **Amends:** [ADR 0008](0008-spring-security.md) (web login) and [ADR 0015](0015-jwt-bearer-api.md) (`POST /api/auth/token`) · **Related:** [ADR 0014](0014-contract-versioning.md) (contract 2.1.0)

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
- **Client IP behind Render and Cloudflare (#133):** on Render a request goes client → Cloudflare → Render's internal proxy → application. The first version of this ADR (#130) relied on `server.forward-headers-strategy=native` alone. Tomcat skipped Render's internal proxy but stopped at Cloudflare's IP, so the first deploy counted `104.22.136.75` (Cloudflare) instead of the user's `46.6.116.139`, and everyone behind the same edge shared one IP.
  - **`ClientIpResolver`:** now builds, through `ClientIpAuthenticationDetailsSource`, the details that both entry points pass to the provider.
  - **When the header counts:** if the connection does not come from a trusted proxy, its address is the client and `X-Forwarded-For` is ignored. Otherwise the header is walked right to left, skipping trusted proxies, and the first address that is not one is the client. Whatever a client adds stays to the left and does not count.
  - **Trusted proxies:** `app.security.trusted-proxies`, CIDR ranges matched with Spring Security's `IpAddressMatcher`. They are the internal ranges plus Cloudflare's official IPv4 and IPv6 ranges (https://www.cloudflare.com/ips-v4 and /ips-v6, downloaded on 2026-11-23).
  - **No DNS lookups:** entries are checked to be IP literals first, because `IpAddressMatcher` would resolve a host name through DNS.
  - **`native` stays** so that Tomcat still applies `X-Forwarded-Proto`. Its result, the Cloudflare edge as the connection address and the client left in the header, is what the resolver reads.
  - **Not `RemoteIpValve`'s `trusted-proxies`:** it takes a single regular expression, and 22 CIDR ranges, IPv6 included, are error-prone that way.
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
- **Cloudflare's ranges change now and then:** a range missing from `app.security.trusted-proxies` would bring back the shared IP for traffic through it. `node scripts/check-cloudflare-ips.mjs` compares the configuration with the official lists and says what to add or remove. It needs `www.cloudflare.com` on the network and is run by hand, not in the CI. If Render ever put another proxy in front, its ranges would be added the same way.
- **No notification:** there is no CAPTCHA, no notification to the user and no permanent lock.

# 0018 · Limit on customer sign-ups

- **Status:** accepted · 2026-11-23
- **Issues:** #131 · **Amends:** [ADR 0012](0012-customer-self-sign-up.md) (accepted risk "no rate limit") · **Related:** [ADR 0017](0017-login-attempt-limit.md) (client IP and in-memory counters), [ADR 0014](0014-contract-versioning.md) (contract 2.2.0)

## Context

The public customer sign-up (`/signup` on the web, `POST /api/customers` on the API, #103) had no limit, a risk accepted in ADR 0012. With the application public on Render, anyone could create customers, accounts and users without end, and since everything lives in memory that could also exhaust the 512 MB instance. The issue was split from #130 by the user on 2026-11-20: a sign-up does not guess passwords, so it needs its own mechanism.

## Decision

- **Policy, agreed with the user on 2026-11-23:**
  - At most **3 successful sign-ups per client IP** in the last hour.
  - At most **50 successful sign-ups from all IPs together** in the last hour, so that someone with many IPs cannot keep filling the memory.
  - Both windows slide: there is room again when the oldest sign-up of the hour expires.
  - **Failed sign-ups do not count** (duplicate document, username taken, short password, business rule): they use no memory, and a user fixing a typo is not punished.
- **Configurable:** `app.security.sign-ups.max-per-ip`, `max-total` and `window` (defaults 3, 50 and 1h); `max-total` cannot be lower than `max-per-ip`.
- **Where:** an input-adapter concern, like the login limit; the domain and the application layer do not change. `SignUpLimit` (in memory, with the application's `Clock`) lives in the root of `adapter.in`. `CustomerRegistration`, shared by the web and REST adapters and already `synchronized`, reserves a slot before calling the use case and releases it if the sign-up fails, so a burst of simultaneous requests cannot exceed either limit.
- **Client IP:** the same `ClientIpResolver` as the login limit (#133), now a bean with `resolve(HttpServletRequest)`.
- **Answers:**
  - **API:** 429 `ProblemDetail` (`TooManySignUps`) with `Retry-After` in seconds.
  - **Web:** a message in the form with the minutes left. The web checks the limit, without reserving, already at step 1, so nobody fills in the three steps for nothing; the last step reserves the slot, since the limit may fill up meanwhile.
  - Neither says which limit was reached.

## Alternatives considered

- **Reusing `LoginAttempts`:** its semantics differ (failures per user and IP, cleared on success, lock counted from the last failure). A small sibling class is clearer than parameterising it.
- **A rate-limiting library (Bucket4j, Resilience4j):** a new dependency for two counters; as in ADR 0017, it fits better with several limits or shared storage.
- **CAPTCHA or e-mail verification:** an external service and more friction for a practice project.
- **A cap measured in memory (bytes):** hard to measure reliably in the JVM; the number of sign-ups is a good enough proxy.

## Consequences

- **The global cap can be used against everyone:** someone with many IPs can fill it and stop legitimate sign-ups for up to an hour. Accepted: it is what keeps the memory bounded, and login and operations keep working.
- **Shared IPs:** a family or office behind one IP (NAT) shares the quota of 3.
- **In memory:** the counters reset on restart and are not shared between instances.
- **IPv6:** the per-IP counter keys on the full address, so a client with a /64 can rotate addresses (same gap as the login limit, #133 review); the global cap still bounds it.
- The full-context test and the contract tests raise both limits: all their sign-ups come from `127.0.0.1`.

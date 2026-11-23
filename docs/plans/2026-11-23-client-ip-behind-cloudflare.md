# Plan: Count the client's IP, not Cloudflare's, behind Render

- **Issue:** [#133](https://github.com/dawnrock/Proyect-DDD-Claude/issues/133)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-23
- **Status:** done
- **Total estimate:** ~60 min (7 tasks)

## Goal

**Current behaviour:** on Render, the login attempt limit of #130 (ADR 0017) counts a Cloudflare edge's IP instead of the client's. After three failures from `46.6.116.139`, the log showed `… desde 104.22.136.75`, which is in `104.16.0.0/13`, one of Cloudflare's official ranges.

**Why:** the chain is client → Cloudflare → Render's internal proxy (10.x) → application. With `server.forward-headers-strategy=native`, Tomcat only skips internal addresses, so it stops at Cloudflare's IP in `X-Forwarded-For`. As a result:
- every visitor behind the same edge shares one IP;
- anyone there can lock `admin` out with 3 failures;
- 10 failures from different people block them all.

**Expected behaviour:** the counted IP is the client's (`46.6.116.139` for `X-Forwarded-For: 46.6.116.139, 104.22.136.75`), and a client cannot choose it by adding addresses of its own.

### Out of scope

- Sign-up limits (#131, which will reuse the resolved IP).
- Downloading Cloudflare's list at build or run time: it changes rarely, so it lives in the configuration with its source and date.
- `Monolithic`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| adapter.in (shared security support) | `ClientIpResolver` (new), `TrustedProxiesProperties` (new) | The client's IP from the connection address and `X-Forwarded-For`. If the connection does not come from a trusted proxy, its address is the client and the header is ignored. Otherwise the header is walked from right to left, skipping trusted proxies; the first address that is not one is the client. Trusted proxies are CIDR ranges (`app.security.trusted-proxies`) matched with Spring Security's `IpAddressMatcher`, which understands IPv4 and IPv6. Each entry is checked to be an IP literal before matching, because `IpAddressMatcher` resolves host names through DNS. An entry that is not an IP, or a header made only of proxies, falls back to the connection address |
| adapter.in | `ClientIpAuthenticationDetailsSource` (new) | `WebAuthenticationDetails(resolvedIp, sessionId)`: the details that `ThrottledAuthenticationProvider` already reads |
| adapter.in · web · rest | `SecuritySupport`, `WebSecurityConfig`, `TokenController` | Beans for the resolver and the source; the web form login uses it (`authenticationDetailsSource`) and so does the token endpoint |
| config | `application.properties` | `app.security.trusted-proxies`: the internal ranges (10/8, 172.16/12, 192.168/16, 100.64/10, 127/8, ::1, fc00::/7) and Cloudflare's 15 IPv4 and 7 IPv6 ranges, downloaded from https://www.cloudflare.com/ips-v4 and /ips-v6 on 2026-11-23. `server.forward-headers-strategy=native` stays for `X-Forwarded-Proto` |
| scripts | `scripts/check-cloudflare-ips.mjs` (new) | Compares the configured ranges with the official lists and reports any difference, to run by hand from time to time |
| docs | ADR 0017, `docs/ia/*` | The amended decision and how to update the list |

**Why not Tomcat's own setting:** `RemoteIpValve` takes the trusted proxies as a regular expression, and turning 22 CIDR ranges, IPv6 included, into one is error-prone. Resolving the IP in the shared security support is plain, testable Java with CIDR, and it is where #131 will need it too.

**How it combines with `native`:** Tomcat's valve still runs first. On Render it sets the connection address to Cloudflare's IP and leaves `46.6.116.139` in `X-Forwarded-For`. The resolver then trusts Cloudflare's range and returns `46.6.116.139`. T1's real-server test checks this exact interplay.

## Tasks

### [x] T1 — Test that reproduces the bug (~10 min)

- **Layer:** tests
- **Files:** `DddLabApplicationTests` (`ClientIpBehindProxy`)
- **Depends on:** —
- **What to do:**
  1. New test against the real server:
     - three wrong logins for `alan` with `X-Forwarded-For: 198.51.100.20, 104.22.136.75` lock the client `198.51.100.20`;
     - then `alan` with `X-Forwarded-For: 198.51.100.21, 104.22.136.75` (another client behind the same Cloudflare edge) can still log in.
  2. Today it fails, because both count as `104.22.136.75`. Commit it red only if the build allows it; otherwise keep it with T3, which makes it green.
- **Acceptance criteria:**
  - [x] Seen red for that reason.

### [x] T2 — Client IP resolver (~10 min)

- **Layer:** adapter.in
- **Files:** `ClientIpResolver`, `TrustedProxiesProperties`, `ClientIpResolverTests`
- **Depends on:** —
- **What to do:**
  1. Tests first (plain JUnit):
     - a direct client ignores the header;
     - Render's internal proxy, then Cloudflare, then the client gives the client;
     - a client that adds addresses of its own (`1.2.3.4, 46.6.116.139, 104.22.136.75`) still gets `46.6.116.139`;
     - Cloudflare over IPv6;
     - a header made only of proxies falls back to the connection address;
     - an entry that is a host name or garbage falls back to the connection address, with no DNS lookup;
     - spaces around the entries are ignored.
  2. Implement with `IpAddressMatcher` and an IP-literal check.
- **Acceptance criteria:**
  - [x] Tests green.

### [x] T3 — Use it in the web login and the API token (~10 min)

- **Layer:** adapter.in · adapter.in.web · adapter.in.rest
- **Files:** `ClientIpAuthenticationDetailsSource`, `SecuritySupport`, `WebSecurityConfig`, `TokenController`, `WebSecurityTests`, `TokenControllerTests`
- **Depends on:** T2
- **What to do:**
  1. Tests first (`@WebMvcTest`, without Tomcat's valve), as if the request came through the proxies (connection from `10.0.0.5`, `X-Forwarded-For: <client>, 104.22.136.75`):
     - a form login is locked for that client, and another client behind the same edge is not;
     - the same for `POST /api/auth/token`.
  2. Beans in `SecuritySupport`; `authenticationDetailsSource` in the form login; the token controller uses the source.
- **Acceptance criteria:**
  - [x] Tests green; T1 green; the existing tests unchanged.

### [x] T4 — Official ranges in the configuration (~5 min)

- **Layer:** config
- **Files:** `application.properties`, `TrustedProxiesPropertiesTests` (or a case in `ClientIpResolverTests` that loads the real file)
- **Depends on:** T2
- **What to do:**
  1. Test: the configured list trusts `104.22.136.75`, Render's `10.x`, and an address from each Cloudflare IPv6 range; it does not trust `46.6.116.139`.
  2. The list with its source and the download date in a comment.
- **Acceptance criteria:**
  - [x] Test green.

### [x] T5 — Script to check the list against Cloudflare (~5 min)

- **Layer:** scripts
- **Files:** `scripts/check-cloudflare-ips.mjs`
- **Depends on:** T4
- **What to do:**
  1. Download `ips-v4` and `ips-v6` and compare them with the Cloudflare ranges in `application.properties`. Print what to add and what to remove, and end with a non-zero exit code if they differ. It needs `www.cloudflare.com` on the network; it is not part of the CI.
  2. Run it: no differences today.
- **Acceptance criteria:**
  - [x] It reports no difference.

### [x] T6 — ADR 0017 and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0017-login-attempt-limit.md`, `docs/ia/decisiones.md`, `docs/ia/pendientes.md`, `docs/ia/historial.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T1-T5
- **What to do:**
  1. ADR 0017:
     - add the client IP resolution and the Cloudflare finding to the decision;
     - in the consequences, how to update the list.
  2. `pendientes.md`:
     - replace the "check on Render" item with a re-check after this deploy;
     - drop the item about clients on internal addresses, now covered by the resolver, which only trusts the header from proxies.
  3. Next step: the user checks Render's log, then #131.
- **Acceptance criteria:**
  - [x] The CI status-file check passes.

### [x] T7 — Review fixes (~10 min)

- **Layer:** adapter.in · tests · docs
- **Files:** `ClientIpResolver`, `ClientIpAuthenticationDetailsSource`, `ClientIpResolverTests`, `ClientIpAuthenticationDetailsSourceTests` (new), `docs/ia/pendientes.md`
- **Depends on:** T1-T6
- **What to do:** findings of the `revisor-ddd` review (none blocking):
  1. **Repeated headers.** With several `X-Forwarded-For` headers, `getHeader` returned only the first, which the client can write. They are now joined in order, with a test using two headers.
  2. **Strict IP literals.** The IPv6 pattern let through strings like `:::` or `1:2`, which ended up as the client's "IP". After the pattern, `InetAddress` now parses the entry: with a colon, Java treats it as an IPv6 literal and never queries the DNS. An invalid entry falls back to the connection address. Tests for those strings, for an IPv6 client, for an empty entry in the middle and for `::ffff:10.0.0.1`.
  3. **Pending item.** An IPv6 client can rotate addresses within its /64 to get past the per-IP limit.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green; the local browser check re-run.

**Deviations:**

- **T1:** its real-server test was committed with T4, the task that makes it green. It needs the trusted-proxies list, and the build cannot be committed red.
- **T6:** the "clients on internal addresses" pending item was reworded instead of dropped. The internal ranges are trusted proxies, so it still holds in principle.

## Global acceptance criteria

**Specific:**

- [x] Through Render's chain (internal proxy → Cloudflare), the counted IP is the client's, both on the web and in the API.
- [x] A direct client cannot choose its IP with `X-Forwarded-For`, and one behind the proxies cannot either: addresses it adds stay to the left and do not count.
- [x] No DNS lookup comes from a forwarded header.
- [ ] After the deploy, the user's lock warning on Render shows their own public IP *(checked by the user after merging)*.

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [x] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [x] No dependencies added beyond the planned ones (or justified in an ADR): none added.
- [x] If the API changes: n/a (same responses; only which IP is counted).
- [x] Any new business term is in `docs/dominio/glosario.md`: n/a.
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **What Render really sends:** the plan assumes `X-Forwarded-For` holds `<client>, <Cloudflare edge>` when it reaches the internal proxy. The lock warning proves that Cloudflare's IP is the rightmost untrusted entry, but not what lies to its left. The lock warning after the deploy settles it. If the header held no client address, the fallback would be `CF-Connecting-IP`, which Cloudflare always overwrites, read only when the connection comes from a Cloudflare range.
- **Cloudflare changing its ranges:** a missing range would bring back the shared IP for traffic through that range. `check-cloudflare-ips.mjs` and the ADR say how to update the list.
- **Sessions in `WebAuthenticationDetails`:** the source keeps the session id as Spring's own does, so nothing else that reads the details changes.

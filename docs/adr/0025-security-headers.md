# 0025 · Content-Security-Policy and security headers

- **Status:** accepted · 2026-11-27
- **Issue:** #206 (third of the OWASP security series: #204 Dependabot, #205 Find Security Bugs) · **Amends:** [ADR 0016](0016-web-frontend-libraries.md) (Alpine.js is removed) · **Related:** [ADR 0008](0008-spring-security.md) (the two security chains), [ADR 0024](0024-oauth2-oidc-auth0.md) (Auth0 redirects and Swagger UI's PKCE)

## Context

Reviewing the project against the OWASP Top 10 (A05, security misconfiguration), the web had no Content-Security-Policy (pending since #111 and #124), no `Referrer-Policy` and no `Permissions-Policy`. Spring Security already sent `X-Content-Type-Options`, `X-Frame-Options`, the cache headers and HSTS on secure requests.

Everything the web loads already came from its own origin, except Swagger UI (jsDelivr, with SRI). Three things stood in the way of a strict policy:

- **Alpine.js** evaluates its expressions with `new Function`, so its standard build needs `'unsafe-eval'`. It only drove two behaviours: the show-password button and the account actions accordion.
- **Inline code:** the login page had two inline scripts (the splash that uncovers the intro before the first paint, and the import map of the intro module), Swagger UI's page had its start-up script inline, and two templates had `style` attributes.
- **Auth0:** logging out and the Auth0 sign-up post a form whose redirect ends on the tenant, and Swagger UI asks the tenant for a token (PKCE).

## Decision

Decided with the user on 2026-11-27: **Alpine.js is removed** and the policy is **enforced from day one** (`Content-Security-Policy`, not `-Report-Only`).

- **Three policies**, built in `adapter.in.ContentSecurityPolicies` (root of `adapter.in`, because both chains use it):

  | Where | Policy |
  | ----- | ------ |
  | Web (every page of the web chain) | `default-src 'self'; script-src 'self' 'nonce-…'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'; form-action 'self' <auth0>` |
  | Swagger UI (`/api-docs/**`) | `default-src 'self'; script-src 'self' https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.34.1/; style-src 'self' https://cdn.jsdelivr.net/npm/swagger-ui-dist@5.34.1/ 'unsafe-inline'; img-src 'self' data:; connect-src 'self' <auth0>; object-src 'none'; base-uri 'none'; frame-ancestors 'none'` |
  | API (`/api/**`) | `default-src 'none'; frame-ancestors 'none'` (OWASP REST Security Cheat Sheet) |

  `<auth0>` is the tenant's origin (`Auth0Properties.baseUrl()`), only when Auth0 is configured. `data:` in images because Bootstrap's CSS draws form icons as `data:` SVGs. `form-action` lists Auth0 because Chromium also applies it to the redirect that follows a form post. `frame-ancestors 'self'` matches the `X-Frame-Options: SAMEORIGIN` the H2 console needs.
- **No CSP on the H2 console** (`/h2-console/**`): it uses its own inline scripts and frames, only exists locally and only for administrators; `prod` turns it off.
- **A nonce per request** for the only inline script left, the login's import map (an import map cannot be a file, and its URL carries a content hash, so a fixed hash in the policy would break on every change). `adapter.in.web.CspNonce` creates 16 random bytes (base64url) on first use and keeps them as a request attribute; `CspNonceAdvice` exposes them as `cspNonce` for `th:nonce`; the header writer of `WebSecurityConfig` reads the same attribute when the response is committed.
- **No inline code:** the splash script moved to `static/js/splash.js`, loaded without `defer` at the same place; Swagger UI's start-up to `static/api-docs/init.js`; the sign-up progress bar uses a class per step; the eye-off icon uses `hidden`.
- **htmx hardened** through `htmx-config`: `includeIndicatorStyles: false` (its injected `<style>` would be blocked; no template uses indicators), `allowEval: false`, `allowScriptTags: false`, and `attributesToSettle` without `style` (when settling a swap, htmx copied the old element's `style` attribute, left by Motion's animations, with `setAttribute`, which `style-src 'self'` blocks: the only violation the browser check found).
- **Alpine.js replaced by plain JavaScript** in `app.js`, with listeners on `document` so that it also works on content htmx inserts: the show-password button (`data-password-toggle`, revealed on `DOMContentLoaded` and `htmx:load`) and the accordion (`data-action-panels`, a `toggle` listener in the capture phase).
- **Both chains:** `Referrer-Policy: strict-origin-when-cross-origin` and `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=()`. HSTS stays Spring Security's default (only on secure requests).
- **Guard rails:** `TemplatesCspTests` fails if a template gets a `style` attribute, an `on*=` handler, an Alpine attribute or an inline `<script>` without `th:nonce`, or if a static page gets any inline script or style (except `api-docs/oauth2-redirect.html`, copied unchanged from swagger-ui-dist).

## Alternatives

- **`Content-Security-Policy-Report-Only` first:** more cautious, but without a reporting endpoint only the browser console would see the reports; the surface is small and is covered by MockMvc tests and a Playwright walk-through.
- **Keep Alpine.js with `'unsafe-eval'`:** a weaker `script-src` for two small behaviours.
- **Alpine.js CSP build:** a new dependency, and its expressions would have to move to `Alpine.data` methods anyway.
- **Hashes instead of a nonce:** break whenever the import map's versioned URL changes.
- **COOP/COEP:** `Cross-Origin-Opener-Policy` would cut Swagger UI's OAuth popup off from its opener; left out.

## Consequences

- No policy contains `'unsafe-eval'`; only Swagger UI's `style-src` contains `'unsafe-inline'` (Swagger UI sets inline styles).
- New templates must keep the rules: no `style`, no inline handlers, inline scripts only with `th:nonce="${cspNonce}"`. `TemplatesCspTests` says so at build time.
- A new external origin (a CDN, an analytics script, another identity provider) has to be added to `ContentSecurityPolicies` and its tests on purpose.
- jsDelivr is allowed only for the pinned `swagger-ui-dist@5.34.1/` path, the same version `index.html` loads with SRI: upgrading Swagger UI changes both (and its SRI hashes), and may need new sources in its policy; upgrading htmx (#214) must keep the four `htmx-config` settings.
- One less frontend dependency (ADR 0016). As a side effect, the show-password button now also works on the sign-up step swapped by htmx, where Alpine.js did not initialise it.
- To check after a deploy, by the owner: `curl -sI https://<service>/login` shows the CSP and `Strict-Transport-Security` through Render and Cloudflare, and logout and the sign-up with Auth0 still work (`form-action`).
- Proven by breaking it: a `style` attribute added to `signup/progress.html` fails `TemplatesCspTests`; removing the H2 exclusion fails `WebSecurityTests.theH2ConsoleHasNoPolicy`.

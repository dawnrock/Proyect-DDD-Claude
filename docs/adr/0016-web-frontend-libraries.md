# 0016 · Frontend libraries for the Thymeleaf web

- **Status:** accepted · 2026-11-18
- **Issue:** #124 (base), followed by #125 and #126 (htmx) · **Complements:** [ADR 0005](0005-thymeleaf.md)

## Context

The web (`adapter.in.web`) is HTML rendered on the server with Thymeleaf and a single hand-written stylesheet. It has no JavaScript, no icon library and no animations. The user wants a more modern interface: htmx and Alpine.js for interactivity, Lucide icons, animations with the View Transitions API and Motion, and Bootstrap as the design system. ADR 0005 still holds: server-side rendering, no separate frontend project and no Node in the build.

## Decision

- **Libraries and versions:**

  | Library | Version | Role |
  | ------- | ------- | ---- |
  | Bootstrap | 5.3.8 | Design system, dark theme (`data-bs-theme="dark"`). Every screen uses its components |
  | htmx | 2.0.11 | Partial requests that swap server-rendered fragments (#125, #126) |
  | Alpine.js | 3.17.5 | Small pieces of client state in the templates (account action accordion) |
  | Lucide (`lucide-static`) | 1.67.0 | Icons through its SVG sprite |
  | Motion | 14.14.0 | Entrance animations (vanilla JS build, global `Motion`) |
  | Tektur (`@fontsource-variable/tektur`) | 5.3.0 | The brand's typeface: the login intro (#178, [ADR 0022](0022-brand-identity.md)), and headings and amounts on every page since #185 ([ADR 0023](0023-brand-ui.md)) |
  | Sora (`@fontsource-variable/sora`) | 5.3.0 | Text typeface of the web (#185, [ADR 0023](0023-brand-ui.md)) |
  | View Transitions API | — (browser) | Transitions between pages with `@view-transition { navigation: auto; }` in CSS |

- **Delivery through WebJars**, chosen by the user: Maven dependencies with pinned versions, served by Spring Boot at `/webjars/**`. `webjars-locator-lite` makes the URLs version-agnostic (`/webjars/bootstrap/css/bootstrap.min.css`), so an upgrade only touches `pom.xml`.
- **Motion is copied into the repository** (`static/vendor/motion/`, with its MIT licence and a `README.md` giving the version and the sha512 checked against the npm registry), because it has no published WebJar. Since #178 the Tektur font goes the same way (`static/vendor/tektur/`, with its SIL Open Font License; [ADR 0022](0022-brand-identity.md)), and since #185 the Sora font (`static/vendor/sora/`; [ADR 0023](0023-brand-ui.md)).
- **Icons as a server-rendered sprite:** the `fragments/icons :: icon(name)` fragment writes `<svg><use href="/webjars/lucide-static/sprite.svg#name"/></svg>`. No JavaScript is needed, the icons do not flash on load, and they keep working inside htmx swaps. The `lucide` JS package would need `createIcons()` again after every swap.
- **Progressive enhancement:** every page and form works without JavaScript. The account actions stay as `<details>`, and Alpine.js only adds the accordion; htmx only changes how a response is shown: the account detail answers a fragment when the request carries `HX-Request` and the usual Post/Redirect/Get otherwise (#125), and every other page is a full page that `hx-boost` swaps into the body (#126). Animations do not run under `prefers-reduced-motion`.
- **How htmx requests are answered (#125):** the controller reads the `HX-Request` header. With it, an operation answers a fragment of the same template (`accounts/detail :: panel`), with the notice in the model and the forms emptied after a success; without it, the usual Post/Redirect/Get with flash attributes. The forms keep `th:action`, so the hidden CSRF field travels with `hx-post` and no extra header is needed. An expired session answers htmx with 401 and `HX-Redirect: /login` (`HtmxLoginRedirect`), and error pages (403, 404) replace the page body (`app.js`).
- **Navigation with `hx-boost` (#126), fragments only where needed:** every full page carries `hx-boost` with a view transition on its `<body>`, so links and forms swap the body and update the title and the URL while the controllers keep answering full pages and redirects; a new screen needs no htmx code. Fragments, like the account detail's panel (#125), are kept for interactions that change only part of a page; their explicit `hx-post` wins over the boost. htmx's history cache is off (`historyCacheSize: 0`, `refreshOnHistoryMiss: true` in the `htmx-config` meta), so no copy of a page with balances or IBANs stays in `sessionStorage`, and the back button reloads from the server. Chosen by the user on 2026-11-20 because it scales with the number of screens.
- **htmx 2.x, not 4.x:** htmx 4 (2026-08) changes events and attribute inheritance; 2.x is maintained and its documentation is stable. Moving to 4 is a later decision.
- `/webjars/**`, `/vendor/**` and `/js/**` are public in `WebSecurityConfig`, like `/css/**` (and `/brand/**` since #178).

## Alternatives

- **CDN with SRI hashes**, as Swagger UI does since #111: no dependencies, but the browser needs internet and the pages run third-party code from another origin.
- **Copying every library into `static/vendor`:** no dependencies and the smallest jar, but third-party files in the repository and manual upgrades for all of them. Only Motion (and, since #178 and #185, the Tektur and Sora fonts) goes this way.
- **A SPA (React, Angular, Vue)** consuming the REST API: a second project, a Node build and the focus away from the domain. ADR 0005 already discarded it.
- **Tailwind CSS:** it needs a build step (Node or its standalone CLI) to generate the CSS.
- **Bootstrap's collapse instead of `<details>`:** it needs JavaScript to open a panel at all.

## Consequences

- The jar grows by about 14.5 MB: `lucide-static` alone weighs 11.5 MB because it ships fonts and loose SVGs besides the sprite. If that matters for the Render image, the sprite can be copied into `static/vendor` and the dependency dropped.
- Motion is upgraded by hand: a new tarball, its integrity checked, and its `README.md` updated.
- The look changes from the custom dark theme of #48 to plain Bootstrap with its dark mode. `app.css` only keeps what Bootstrap does not cover.
- There is still no Content-Security-Policy (pending since #111). With every script served from the application's own origin, a `script-src 'self'` policy becomes possible, except that the Alpine.js standard build evaluates expressions with `new Function` and would need `'unsafe-eval'`, or its CSP build.

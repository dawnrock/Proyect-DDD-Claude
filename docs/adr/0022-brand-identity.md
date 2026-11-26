# 0022 · monA Bank brand identity: logo, intro and typeface

- **Status:** accepted · 2026-11-25 (design chosen by the user with prototypes)
- **Issue:** #178 · **Related:** [ADR 0005](0005-thymeleaf.md) (Thymeleaf web), [ADR 0016](0016-web-frontend-libraries.md) (frontend libraries)

## Context

The web said «Cuentas corrientes» in its header, «Mon-A Bank» in its texts and «Fase 2 · arquitectura hexagonal» in its footer: no identity of its own, and a footer that described the project, not the bank. The user wanted a brand built around the name's pun («monA» sounds like *money*) and an animated intro before the login doors. The design was iterated with HTML prototypes, kept as reference in [`docs/brand/`](../brand/README.md).

## Decision

- **Name:** written **monA Bank** everywhere the user reads it (templates, error messages, contract texts). The A, the only capital, is the protagonist. Code names (`MonABankIbans`, `isMonABank()`) are domain language and do not change.
- **Logo:** «monA» in [Tektur](https://fonts.google.com/specimen/Tektur) 700, with «mon» at 75 % of the A, and «Bank» in Tektur 400 below it. A market chart is painted inside the letters, clipped by each glyph: the top band of m and o in grey (flat market), the n's top bar and right stem down to the baseline in coral red (the drop), and the A's left leg in turquoise, continued past the apex by an arrow with the leg's width and slant (the rise). Colours and proportions are in `docs/brand/README.md`.
- **SVG files generated from the outlines:** `static/brand/` holds the stacked logo (welcome screens), the horizontal one (header) and the favicon. Their letters are paths, so they need no font and work in `<img>` and as favicon. `scripts/build-brand.mjs` writes them; it is run by hand when the logo or the typeface changes, and its output is committed.
- **One geometry for both drawings:** where the chart goes is computed by `static/js/brand-geometry.js`, an ES module of pure functions over an `ink(x, y)` predicate. The intro builds that predicate from canvas pixels; the generator builds it from the glyph outline (point in polygon). So the intro's last frame and the static logo are the same drawing. The module is tested with `node:test` (`scripts/brand-geometry.test.mjs`, run by the CI).
- **Tektur vendored:** the variable font (weight 400–900, latin) from `@fontsource-variable/tektur` 5.3.0 is copied into `static/vendor/tektur/` with its SIL Open Font License, like Motion, because no WebJar exists. It is only downloaded when something uses it: the intro, which animates the weight, and, since #185, the headings and amounts of every page ([ADR 0023](0023-brand-ui.md)).
- **opentype.js only at development time:** the generator downloads `opentype.js` 2.0.0 and the static instances of `@fontsource/tektur` 5.3.0 (opentype.js does not apply a variable font's variations) with `npm pack`, which checks their integrity, into a temporary folder. They are not project dependencies: the build and the CI never need them.
- **The intro:** `static/js/intro.js`, a module loaded only by the login doors (`/login` without `form`). It gets the versioned URL of the geometry through an import map that Thymeleaf fills, so the year-long cache of static resources never serves an old copy. The layer arrives `hidden`, first in the doors' body, and a small inline script right after it shows it before the first paint (so the welcome never flashes) on a full load without reduced motion, removing it again if `intro.js` has not started within 2.5 s; the module then decides whether it plays: on every full load of the doors (since 2026-11-26; until then once per browser session), not with `prefers-reduced-motion: reduce` and only if Tektur loads; without JavaScript it is never seen. Esc or a click removes it. Being a module, it is evaluated once per document, so htmx-boosted navigations never replay it. It lasts 5 s (3 s until 2026-11-26, when the user asked for time to see it): twelve banking and stock-market words stay still for a second, lose their endings, their initials fly to the logo, the chart is painted, and the logo stays still for another second. Then (since 2026-11-26, so that the doors do not appear with a jump) the logo shrinks onto the welcome's own logo, measured on the page, and «Bank» fades from under the A to beside it, as in `monabank-horizontal.svg`; the layer is removed with the page logo exactly underneath, and «Bienvenido a», the backdrop and the doors appear in turn (`html[data-intro]` in `app.css`; `app.js` skips its entrance animation meanwhile). About 5.2 s plus 1.5 s for the page.
- **No footer:** the phase footer is removed from every page.

## Alternatives

- **The name as styled text** (web font + CSS): CSS can colour whole letters or gradients, but not paint a stroke clipped to part of a glyph, nor add the arrow.
- **The logo drawn on a canvas at runtime**, like the intro: needs JavaScript and the font on every page, and a favicon cannot be a canvas.
- **SVGs drawn by hand:** they would drift from the intro; the shared geometry keeps both identical.
- **A video, a GIF or Lottie for the intro:** heavier, blurry at some sizes, the words could not adapt to each screen, and Lottie is another library.
- **Tektur from Google Fonts' CDN:** a third-party origin and an internet dependency in the browser, which ADR 0016 avoids for the frontend.

## Consequences

- The jar grows by about 12 KB (the font) and 9 KB (the SVGs).
- Changing the logo means editing `brand-geometry.js` or the generator and running `node scripts/build-brand.mjs`; the intro picks up the same change. Upgrading Tektur means a new copy in `static/vendor/tektur/` and regenerating the SVGs.
- The animation itself has no automated test (no browser test suite; that would need its own ADR): its markup is tested with MockMvc, the geometry with `node:test`, and the animation is checked by hand in a browser.
- `/brand/**` is public, like the other static resources.

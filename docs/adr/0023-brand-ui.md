# 0023 · The brand's UI language: the A's slant, logo glyphs and Sora

- **Status:** accepted · 2026-11-25 (direction chosen by the user with sketches on a design canvas)
- **Issue:** #185 · **Related:** [ADR 0016](0016-web-frontend-libraries.md) (frontend libraries), [ADR 0022](0022-brand-identity.md) (brand identity)

## Context

After #178 the web had the monA Bank logo, intro and favicon, but every screen still used Bootstrap's components as they come: rectangular buttons on the login doors, on the account actions and in the forms, the same as any Bootstrap site. The user wanted a look of its own, modern and tied to the brand. Three directions were sketched for the doors, the account list and the account detail: **A · Chamfer** (Tektur's 45° cut corners), **B · Chart** (the logo's market line drawn with real balances) and **C · The A** (the slant of the A's legs). The user chose C, with the darkened A behind the welcome screen.

## Decision

- **Bootstrap stays the base** (ADR 0016): grid, utilities, forms and validation classes keep working, and the tests that read their markup do not change. The brand is a layer in `static/css/app.css`: the palette maps onto Bootstrap's custom properties (`--bs-body-bg`, `--bs-body-color`, `--bs-secondary-color`, `--bs-border-color`, the font families…) and a few classes of its own are added.
- **Palette** from the logo: ground `#212529`, surface `#2b3035`, line `#343a40`, ink `#eef0f2`, secondary ink `#b9c0c7`, flat grey `#8b949c`, drop coral `#f0545c` and rise turquoise `#1fc8b0`. Rise and drop carry meaning: money in and money out.
- **Shapes from the A:** buttons are trapezoids with both sides of the A, about 12.4° each (`.btn-slant`, with the variants `rise`, `light`, `drop` and `surface`): the left side rises like the A's left leg and the right side falls like the right one (parallelograms until 2026-11-26, when the user asked for both sides of the A); the account actions sit side by side like a track; account cards have their right edge slanted like the A's right leg (`.card-slant`). A button's shape is a `::before` cut with `clip-path`, its top inset being the minimum height (`--slant-h`) times tan 12.4°; as a `clip-path` would also cut an outline, the focus is an `::after` with the same shape as a ring (`evenodd`) 3 px away from the button. The cards are cut with `clip-path` too, so their focus is an inset ring.
- **Logo glyphs as icons** (`fragments/brand.html`, inline SVG from the logo outlines, decorative and coloured with `currentColor`): `rise`, the A's leg with its arrow, for deposits and incoming transfers; `drop`, the n's bar and stem, for withdrawals and outgoing transfers; a rejected movement keeps its glyph in grey; `watermark`, the A in outline, behind account cards and the balance; `backdrop`, the A bigger than the screen and darkened, behind the entry screens (login doors, login form and the three sign-up steps).
- **Typefaces:** Tektur for headings, buttons, balances, amounts and IBANs; **Sora** for every other text (labels, fields, descriptions, notices). Sora 5.3.0 (variable weight 100–800, latin), from `@fontsource-variable/sora`, is copied into `static/vendor/sora/` with its SIL Open Font License and a `README.md` giving the version and the integrity checked against the npm registry, like Tektur and Motion, because no WebJar exists. The OFL lets it be used, embedded and redistributed at no cost; it only forbids selling the font on its own.
- **Both fonts are preloaded** in the common `head` and served from the application's own origin with the year-long cache of static resources.

## Alternatives

- **Direction A (chamfered corners) or B (balance chart):** both sketched; the user chose C.
- **The system font for text:** no new file and almost the same layout, but the small text loses the geometric character that matches Tektur. The user chose Sora knowing it is optional.
- **Tektur for everything:** a display face; tiring in paragraphs, error messages and small labels.
- **Dropping Bootstrap for hand-written CSS or another framework:** the forms, validation feedback and the tests built on Bootstrap's classes would all have to change, for a presentation-only change.
- **Rewriting the account actions as buttons driven by Alpine.js:** the actions are `<details>` so that they open without JavaScript (ADR 0016); they keep that markup and only their look changes.

## Consequences

- Every page downloads Tektur (12 KB) and Sora (33 KB) once; afterwards they come from the cache. Tektur keeps `font-display: block`, which the intro needs, so on a slow first visit headings can appear a moment after the rest.
- New screens use `.btn-slant`, `.card-slant` and the brand fragments instead of `btn-primary`, cards and Lucide arrows for money movements.
- Upgrading Sora means a new copy in `static/vendor/sora/`, its README and this ADR.
- The look has no automated visual test (no browser suite; ADR 0022): the markup is tested with MockMvc and the screens are checked by hand at phone and desktop widths, with and without JavaScript and with reduced motion.

# monA Bank brand

The bank's name is written **monA Bank**: «monA» sounds like *money*, and the A, the only capital letter, is the protagonist. Designed with the user on 2026-11-25 (issue #178).

## Logo

- **Typeface:** [Tektur](https://fonts.google.com/specimen/Tektur) (SIL Open Font License, variable `wght`). «monA» in 700, with «mon» at 75 % of the A's size on the same baseline; «Bank» in 400 at 30 % of the A, right-aligned below.
- **A market chart painted inside the letters**, clipped by each glyph (strokes 1.4 × the stroke thickness):

  | Letter | Painted part | Colour | Meaning |
  | ------ | ------------ | ------ | ------- |
  | m, o | Top band | `#8b949c` | Flat market |
  | n | Top bar and the whole right stem down to the baseline | `#f0545c` | The drop |
  | A | Left leg from the base to the apex, continued past the apex by an arrow with the leg's width and slant (shaft 18 % of the A size, head about 2.6 × the stroke width) | `#1fc8b0` | The rise |

- Letters `#eef0f2`, «Bank» `#b9c0c7`, on the web's dark background `#212529`.

## Intro

A 5-second animation before the login doors: twelve positive banking and stock-market words (Maximize, Markets · Optimism · Net gain · Affluence, Assets · Bull, Bonus · Alpha · Next · Key, Keep) stay still for a second, lose their endings, their initials fly to the letters of the logo, the chart is painted from left to right and the logo stays still for another second before fading out. It plays on every full load of the doors; skipped with reduced motion, without JavaScript or if the font does not load.

## Web interface

Chosen by the user on 2026-11-25 from three sketched directions (issue #185, [ADR 0023](../adr/0023-brand-ui.md)): **C · The A**. The slant of the A's legs is the shape of the interface and the logo's chart gives the icons. Everything lives in `static/css/app.css` (on top of Bootstrap) and `templates/fragments/brand.html`.

| Token | Value | Use |
| ----- | ----- | --- |
| ground | `#212529` | Page background |
| surface | `#2b3035` | Cards, forms, inactive actions |
| line | `#343a40` | Dividers |
| ink / ink-2 | `#eef0f2` / `#b9c0c7` | Text / secondary text |
| flat | `#8b949c` | Neutral, rejected movements |
| drop | `#f0545c` | Money out: withdrawals, outgoing transfers |
| rise | `#1fc8b0` | Money in and the main action |

- **Type:** Tektur for headings, buttons, balances, amounts and IBANs; Sora for the rest.
- **Slant:** about 12.4°, the A's legs. Buttons (`.btn-slant` with `rise`, `light`, `drop`, `surface`) are parallelograms drawn by a skewed `::before`, so the focus outline follows the shape. Account cards (`.card-slant`) have their right edge slanted like the A's right leg.
- **Glyphs** (`fragments/brand`): `rise`, the A's leg with its arrow, for money in; `drop`, the n's bar and stem, for money out; a rejected movement keeps its glyph in grey; `watermark`, the A in outline behind account cards and the balance; `backdrop`, the darkened A bigger than the screen behind the entry screens (login doors, login form, sign-up steps).
- **Screens:** the doors have no header, only the logo, the greeting and the two doors without icons, centred over the backdrop. The account actions are a track of interlocked parallelograms; the open one fills with its colour (rise, drop or ink) and its form goes below.

## Prototypes

[`prototypes/`](prototypes/) holds the two approved prototypes, kept as reference for the implementation. They load Tektur from Google Fonts and are never served by the application.

- [`intro.html`](prototypes/intro.html): the full intro. `?frame=<ms>` draws a single frame.
- [`logo.html`](prototypes/logo.html): the static logo. `?mon=`, `?arrow=` and the `gray`, `red` and `green` parameters try other proportions and colours.

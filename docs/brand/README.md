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

A 3-second animation before the login doors: twelve positive banking and stock-market words (Maximize, Markets · Optimism · Net gain · Affluence, Assets · Bull, Bonus · Alpha · Next · Key, Keep) lose their endings, their initials fly to the letters of the logo, and the chart is painted from left to right. Once per browser session, skipped with reduced motion, without JavaScript or if the font does not load.

## Prototypes

[`prototypes/`](prototypes/) holds the two approved prototypes, kept as reference for the implementation. They load Tektur from Google Fonts and are never served by the application.

- [`intro.html`](prototypes/intro.html): the full intro. `?frame=<ms>` draws a single frame.
- [`logo.html`](prototypes/logo.html): the static logo. `?mon=`, `?arrow=` and the `gray`, `red` and `green` parameters try other proportions and colours.

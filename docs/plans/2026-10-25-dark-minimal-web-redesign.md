# Plan: Dark, minimal web redesign

- **Issue:** [#48](https://github.com/dawnrock/Proyect-DDD-Claude/issues/48)
- **Type:** feature
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-25
- **Approved:** by the user on 2026-10-25
- **Status:** in progress
- **Total estimate:** ~75 min (8 tasks)

## Goal

Restyle the Thymeleaf web UI as a dark, minimal interface in the style of mobile investing apps (no third-party branding), following the mock-up agreed with the user on 2026-10-25: near-black background, large balances, list rows instead of tables, pill-shaped actions whose forms open on demand, and signed, coloured movements. Amounts are shown in Spanish format (`1.000,00 €`). Behaviour does not change: same routes, same Post/Redirect/Get, same validation and error handling.

### Out of scope

- `application`, `domain`, `persistence` and the REST API (and therefore OpenAPI): the issue forbids changes there.
- Showing the segment in the account list: `AccountSummary` has no segment and adding it would change `application` (see Risks).
- Labelling transfer movements as transfers: movements are plain `WITHDRAWAL` / `DEPOSIT` (#7).
- A light theme or a theme switch, and any JavaScript.

## Context

- **Current UI:** one 65-line `static/css/app.css` (light theme), a layout fragment (`fragments/layout.html`: head, header, footer) and four pages: `accounts/list.html`, `accounts/detail.html` (data, transactions table and three always-visible forms), `accounts/new.html`, `error/404.html`. Amounts are rendered as `amount + ' ' + currency` (`150.00 EUR`), in templates and in the controller's flash messages.
- **Formatting helper:** a new `presentation.web.WebFormat` (`@Component("fmt")`), usable from templates as `${@fmt.money(...)}` and injected into `AccountWebController` for the flash messages. Plain JDK formatting, no new dependency:
  - `money(BigDecimal amount, String currency)` → `NumberFormat.getCurrencyInstance(Locale.of("es", "ES"))` with that currency → `1.000,00 €`;
  - `signedMoney(...)` for movements (`+25,00 €`, `−120,00 €`);
  - `maskedIban("ES7921000813610123456789")` → `ES79 •••• 6789`;
  - `initials("Grace Hopper")` → `GH`.
  It takes only application views' plain values (`BigDecimal`, `String`), never domain types (D4).
- **Forms on demand:** each action is a `<details class="action">` whose `<summary>` is the pill. It is `open` when its form has errors after a POST, so an error re-renders the detail page with that form expanded and the other two closed (`th:attr="open=..."` from `#fields.hasErrors('depositForm.*')`, etc.). No JavaScript.
- **List totals:** the controller adds `totals` (balance summed per currency over `listAccounts()`); with today's EUR-only accounts it is a single "Saldo total" line.
- **Tests that change:** about 16 assertions in `AccountWebControllerTests` and 7 in `DddLabApplicationTests` check `150.00 EUR`-style text, `row-rejected` or the flash texts. They are updated to the new format in the same task that changes the markup they check. No behaviour assertion is dropped.
- **Architecture:** `LayeredArchitectureTests` (#12) already guards presentation (D4, D5); `WebFormat` lives in `presentation.web` and uses no domain types.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `presentation.web` | `WebFormat.java` (+ `WebFormatTests.java`) | New formatting helper |
| `presentation.web` | `AccountWebController.java` | Totals for the list; flash messages with `WebFormat` |
| web resources | `static/css/app.css`, `templates/fragments/layout.html`, `templates/accounts/{list,detail,new}.html`, `templates/error/404.html` | Dark theme and new layouts |
| tests | `AccountWebControllerTests.java`, `DddLabApplicationTests.java` | New format and markup |
| `docs` | `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md` | Documentation |

## Tasks

### [x] T1 — `WebFormat` helper (~10 min)

- **Layer:** presentation
- **Files:** `presentation/web/WebFormat.java`, `presentation/web/WebFormatTests.java`
- **Depends on:** —
- **What to do:**
  1. Plain JUnit tests first: `money` (`1000.00 EUR` → `1.000,00 €`; `5.5 USD` → `5,50 US$`, whatever the JDK's es-ES gives, asserted from the same `NumberFormat`), `signedMoney` (`+`/`−`), `maskedIban`, `initials` (one word, two words, extra spaces).
  2. Implement with the JDK only.
- **Acceptance criteria:**
  - [x] Tests green (red first: class missing). No domain imports.

### [x] T2 — Dark theme base and layout (~10 min)

- **Layer:** presentation
- **Files:** `static/css/app.css`, `templates/fragments/layout.html`, `templates/error/404.html`
- **Depends on:** —
- **What to do:**
  1. Rewrite `app.css` around CSS variables (background, surface, hairline, text, muted, positive, negative), system font, `color-scheme: dark`, generous spacing, pill buttons, list rows, form fields on dark surfaces, flash messages, responsive at phone width.
  2. Minimal header (brand only) and footer; 404 page with the same style.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green (the 404 tests still pass); page checked in the browser.

### [ ] T3 — Account list (~10 min)

- **Layer:** presentation
- **Files:** `templates/accounts/list.html`, `AccountWebController.java`, `AccountWebControllerTests.java`
- **Depends on:** T1, T2
- **What to do:**
  1. Controller: `totals` model attribute (balance per currency).
  2. Template: "Saldo total" hero; one row per account with initials, holder, masked IBAN and formatted balance, the whole row linking to the detail; "Abrir cuenta" pill; empty state.
  3. Tests: total shown formatted; rows show masked IBAN, initials and `1.000,00 €`; each row links to its detail.
- **Acceptance criteria:**
  - [ ] Tests green.

### [ ] T4 — Account detail: balance and movements (~10 min)

- **Layer:** presentation
- **Files:** `templates/accounts/detail.html`, `AccountWebControllerTests.java`
- **Depends on:** T1, T2
- **What to do:**
  1. Hero: holder and full IBAN (muted), large balance, segment label in Spanish.
  2. Movements as rows: type in Spanish and date; amount signed (`+` deposit in the positive colour, `−` withdrawal); rejected rows struck through with "Rechazada" in the negative colour; empty state.
  3. Update the detail tests to the new format (`150,00 €`, rejected marker).
- **Acceptance criteria:**
  - [ ] Tests green; rejected movements remain clearly marked.

### [ ] T5 — Actions as pills with forms on demand (~10 min)

- **Layer:** presentation
- **Files:** `templates/accounts/detail.html`, `AccountWebControllerTests.java`
- **Depends on:** T4
- **What to do:**
  1. "Ingresar", "Retirar" and "Transferir" as `<details>`/`<summary>` pills wrapping the existing forms (fields, errors and the "no other accounts" hint unchanged).
  2. A form with errors is rendered `open`; the others closed.
  3. Tests: on GET all three are closed; a field error in the transfer form opens only the transfer `<details>` (likewise for deposit).
- **Acceptance criteria:**
  - [ ] Tests green; every existing form-error test still passes.

### [ ] T6 — Flash messages in the new format (~10 min)

- **Layer:** presentation
- **Files:** `AccountWebController.java`, `AccountWebControllerTests.java`, `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java`
- **Depends on:** T1
- **What to do:**
  1. Build the deposit, withdrawal and transfer flash texts with `WebFormat.money` ("Ingreso de 25,00 € aprobado", "Transferencia de 25,00 € a ES79 •••• 6789 aprobada").
  2. Update the controller and end-to-end tests that check those texts and balances (`150,00 €`, `125,00 €`, `75,00 €`).
- **Acceptance criteria:**
  - [ ] Tests green; rejected flashes keep their text valid for both segments.

### [ ] T7 — New account form (~5 min)

- **Layer:** presentation
- **Files:** `templates/accounts/new.html`
- **Depends on:** T2
- **What to do:**
  1. Same dark field style, primary pill "Abrir cuenta", secondary "Cancelar" link back to the list. Fields, labels and errors unchanged.
- **Acceptance criteria:**
  - [ ] Existing sign-up tests green; page checked in the browser, including a validation error.

### [ ] T8 — Browser check and documentation (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T3-T7
- **What to do:**
  1. With `./mvnw spring-boot:run` (demo data): list, detail, each action (approved and rejected transfer, a field error), sign-up and 404, at desktop width and at phone width (375 px); screenshots shown to the user before committing the documentation.
  2. `fase-1-capas.md`: "Implementado en la #48" (dark theme, `WebFormat`, forms in `<details>`).
  3. Project status: #48 done, next step `/plan-feature 13`, history row.
- **Acceptance criteria:**
  - [ ] Browser checks done at both widths; documents updated.

## Global acceptance criteria

**Specific:**

- [ ] The four screens (list, detail, sign-up, 404) use the dark theme.
- [ ] A form error re-renders the detail with that form open and its message; a rejected withdrawal or transfer is still shown as rejected.
- [ ] Amounts in Spanish format (`1.000,00 €`) on every screen and flash message.
- [ ] No changes in `application`, `domain`, `persistence` or the REST API.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The layered architecture is respected (`LayeredArchitectureTests`; `WebFormat` uses no domain types), and reviewed by `revisor-ddd`.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`check-domain.mjs` without errors) — unchanged.
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added (JDK formatting, plain CSS).
- [ ] If the API changes: OpenAPI — **n/a** (no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md` — n/a (UI only).
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish excepted).
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- **Segment in the list (deviation from the mock-up):** the mock-up showed "Minorista" / "Alto patrimonio" in each list row, but `AccountSummary` has no segment. Options: leave it out (this plan), or add `segment` to `AccountSummary` in a separate back-side issue.
- **Dark only:** the plan ships a single dark theme (`color-scheme: dark`), as in the mock-up; following the system's light/dark preference would need a second palette.
- **Currency symbols:** non-EUR amounts use the JDK's es-ES symbols (`US$`, `GB£`); only EUR accounts can be opened today.
- **Overdraft hint:** the mock-up's "descubierto hasta −1.000,00 €" line is left out because it would duplicate a domain rule (R8) in presentation.

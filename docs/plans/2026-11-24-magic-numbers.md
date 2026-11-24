# Plan: Named constants instead of magic numbers in production code

- **Issue:** [#167](https://github.com/dawnrock/Proyect-DDD-Claude/issues/167)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-24
- **Status:** in progress
- **Total estimate:** ~55 min (8 tasks)

## Goal

Checkstyle's `MagicNumber` rule (Clean Code course, ch. 1 "Searchable names" and ch. 2 "Constants") finds 19 unnamed numbers in `src/main`. They are algorithm rules (IBAN and CCC check digits, NIF control letter), positions in a format (IBAN, NIF), and time conversions rounded up, duplicated in three classes. None of them changes between environments, so they become constants or named methods, not configuration.

- **Improves:** each value gets a searchable name. The rounded-up time conversions are written once, in a method with a name.
- **Must NOT change:** any behaviour. The same IBANs are generated, the same NIFs are accepted or rejected, IBANs are masked the same way, and the same minutes and `Retry-After` seconds are shown. The existing tests prove it without changes.

### Out of scope

- The 248 numbers in tests: test data, more readable as literals. `MagicNumber` will not apply to `src/test` (#168).
- Adding Checkstyle to the build (#168). Here it runs locally in a scratch copy of the project.
- String literals that are not numbers (`"142800"`, `"%010d"`): not flagged by the rule. They already have a comment and are left as they are.
- `Monolithic`, frozen.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `domain.model` | `TaxId.java` | `8` → `CONTROL_LETTER_POSITION`; `% 23` → `% CONTROL_LETTERS.length()` (the modulus is the size of the letter table) |
| `domain.model` | `AccountNumber.java` | `startsWith(MON_A_BANK_PREFIX, 4)` → `BBAN_OFFSET` (`ES` + two check digits) |
| `adapter.out.persistence` | `MonABankIbans.java` | `11`/`10` → `CCC_MODULUS`; `98`/`97` → `IBAN_CHECK_BASE`, `IBAN_MODULUS` (`BigInteger`) |
| `adapter.in` (shared support) | `Durations.java` (new) + `DurationsTests.java` | `minutesRoundedUp(Duration)` and `secondsRoundedUp(Duration)`, never less than one |
| `adapter.in.web` | `LoginController.java`, `SignUpController.java` | Use `Durations.minutesRoundedUp` |
| `adapter.in.rest` | `RestExceptionHandler.java` | Use `Durations.secondsRoundedUp` |
| `adapter.in.web` | `WebFormat.java` | `8`/`4` → `VISIBLE_IBAN_CHARACTERS = 4` (and `2 * VISIBLE_IBAN_CHARACTERS` as the minimum length) |
| docs | `docs/dominio/glosario.md` | Add *BBAN* if it is used in code (part of the IBAN after the country code and check digits) |

**Where the time helper lives.** `adapter.in` is the shared support package of the input adapters: `HexagonalArchitectureTests` already lets `adapter.in.web` and `adapter.in.rest` depend on it (`SegmentLabels`, `LoginAttemptsProperties`). It is not domain logic: it rounds a waiting time for display or for a header.

**Rounding without magic numbers.** `Duration` has no ceiling method. `minutesRoundedUp` takes `toMinutes()` and adds one if the duration is longer than that many whole minutes; `secondsRoundedUp` does the same with seconds. Both return at least 1. The JDK units replace `59`/`60` and `999`/`1000`.

**Safety net.** Everything is already covered: `TaxIdTests`, `AccountNumberTests`, `MonABankIbansTests`, `InMemoryAccountNumberSequenceTests` (R31 check digits), `WebFormatTests` (`maskedIban`), `LoginControllerTests` ("30 minutos"), `SignUpControllerTests` (`tooManySignUps`), `CustomerControllerTests` (`Retry-After: 91`, rounded up) and `TokenControllerTests` (`Retry-After` 1800). Only `Durations` is new and gets its own tests.

## Tasks

### [x] T1 — Baseline (~5 min)

- **Layer:** tests
- **Files:** — (scratchpad only)
- **Depends on:** —
- **What to do:**
  1. `./mvnw verify` green and record the number of tests.
  2. Run Checkstyle `MagicNumber` on `src/main` in a scratch copy: 19 warnings, with file and line.
  3. Check that each warning's behaviour is covered by the tests listed in *Safety net*.
- **Acceptance criteria:**
  - [x] Baseline recorded: 19 warnings, each one mapped to the tests that cover it. (19 warnings confirmed on the code merged in #169; 1,023 test cases; each warning is covered by the tests of *Safety net*)

### [x] T2 — `TaxId`: control letter position and modulus (~5 min)

- **Layer:** domain
- **Files:** `src/main/java/com/dawnrock/ddd/domain/model/TaxId.java`
- **Depends on:** T1
- **What to do:**
  1. `private static final int CONTROL_LETTER_POSITION = 8;` used in `charAt` and in `substring(1, CONTROL_LETTER_POSITION)`.
  2. `% 23` → `% CONTROL_LETTERS.length()`: the modulus is the number of letters in the table, so no extra constant is needed.
- **Acceptance criteria:**
  - [x] `TaxIdTests` green without changes; `check-domain.mjs` without errors. (sabotage: `CONTROL_LETTER_POSITION = 7` makes 9 `TaxIdTests` fail; reverted)

### [x] T3 — `AccountNumber`: where the BBAN starts (~5 min)

- **Layer:** domain
- **Files:** `src/main/java/com/dawnrock/ddd/domain/model/AccountNumber.java`, `docs/dominio/glosario.md`
- **Depends on:** T1
- **What to do:**
  1. `private static final int BBAN_OFFSET = 4;` with a Javadoc (`ES` and the two IBAN check digits), used in `isMonABank()`.
  2. Add *BBAN* to the glossary if the name is kept.
- **Acceptance criteria:**
  - [x] `AccountNumberTests` green without changes. (*BBAN* added to the glossary; sabotage: `BBAN_OFFSET = 3` makes `AccountNumberTests` fail; reverted)

### [x] T4 — `MonABankIbans`: CCC and IBAN moduli (~5 min)

- **Layer:** adapter.out
- **Files:** `src/main/java/com/dawnrock/ddd/adapter/out/persistence/MonABankIbans.java`
- **Depends on:** T1
- **What to do:**
  1. `CCC_MODULUS = 11`: `digit = CCC_MODULUS - sum % CCC_MODULUS`; then `CCC_MODULUS → 0` and `CCC_MODULUS - 1 → 1`. `-1` is ignored by the rule; if the ternary chain still reads badly, extract it to a method with a name.
  2. `IBAN_MODULUS = BigInteger.valueOf(97)` and `IBAN_CHECK_BASE = 98`, keeping the ISO 13616 Javadoc.
- **Acceptance criteria:**
  - [x] `MonABankIbansTests` and `InMemoryAccountNumberSequenceTests` green without changes; the demo IBANs (`ES8099990001450000000001`…) are the same. (the "10 → 1, 11 → 0" rule went to a named method, `asSingleDigit`, as the plan's risk suggested; sabotages: `IBAN_CHECK_BASE = 97` fails 4 tests and dropping the "10 → 1" rule fails 10; reverted; the demo IBAN test of `MonABankIbansTests` is green)

### [x] T5 — `Durations`: waiting times rounded up (~10 min)

- **Layer:** adapter.in
- **Files:** `src/main/java/com/dawnrock/ddd/adapter/in/Durations.java`, `src/test/java/com/dawnrock/ddd/adapter/in/DurationsTests.java`
- **Depends on:** T1
- **What to do:**
  1. Red: tests (with the convention from #166) for `minutesRoundedUp`: exact minutes (30 min → 30), one second over (30 min 1 s → 31), under a minute (20 s → 1) and zero (→ 1). For `secondsRoundedUp`: exact (91 s → 91), one millisecond over (90.001 s → 91), under a second (→ 1). Use `@ParameterizedTest` + `@CsvSource`, as in `src/test/CLAUDE.md`.
  2. Green: `final class Durations` with a private constructor and the two static methods, without numeric literals other than 1.
- **Acceptance criteria:**
  - [x] `DurationsTests` green; ArchUnit green (the class lives in `adapter.in`). (red: `Durations` did not exist; green: 8 cases; total 1,031 = 1,023 + 8)

### [x] T6 — Use `Durations` in the controllers and the REST handler (~5 min)

- **Layer:** adapter.in
- **Files:** `adapter/in/web/LoginController.java`, `adapter/in/web/SignUpController.java`, `adapter/in/rest/RestExceptionHandler.java`
- **Depends on:** T5
- **What to do:**
  1. `Math.max(1, (x.toSeconds() + 59) / 60)` → `Durations.minutesRoundedUp(x)` in `LoginController` and `SignUpController.tooManySignUps`.
  2. `Math.max(1, (retryAfter.toMillis() + 999) / 1000)` → `Durations.secondsRoundedUp(retryAfter)` in `RestExceptionHandler.tooManyRequests`.
- **Acceptance criteria:**
  - [x] `LoginControllerTests`, `SignUpControllerTests`, `CustomerControllerTests` and `TokenControllerTests` green without changes. (8 + 43 + 21 + 8 cases green, unchanged)

### [x] T7 — `WebFormat.maskedIban` (~5 min)

- **Layer:** adapter.in
- **Files:** `src/main/java/com/dawnrock/ddd/adapter/in/web/WebFormat.java`
- **Depends on:** T1
- **What to do:**
  1. `private static final int VISIBLE_IBAN_CHARACTERS = 4;` for the first and last characters that stay visible. The minimum length becomes `2 * VISIBLE_IBAN_CHARACTERS` (`2` is ignored by the rule and says what it means: both ends).
- **Acceptance criteria:**
  - [x] `WebFormatTests` green without changes. (sabotage: `VISIBLE_IBAN_CHARACTERS = 3` makes `WebFormatTests` fail; reverted)

### [ ] T8 — Final check and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `docs/ia/historial.md`
- **Depends on:** T2-T7
- **What to do:**
  1. Run Checkstyle `MagicNumber` on `src/main` again in the scratch copy: 0 warnings.
  2. `./mvnw verify`, `check-domain.mjs`, and start with the `demo` profile to check that the demo IBANs and the health check are unchanged.
  3. Project status and a row in the history.
- **Acceptance criteria:**
  - [ ] 0 `MagicNumber` warnings in `src/main`; the number of tests is the baseline plus the new `DurationsTests`.

## Global acceptance criteria

**Specific:**

- [ ] Checkstyle `MagicNumber` gives 0 warnings in `src/main`.
- [ ] The time conversions of the two controllers and the REST handler use a single named method.
- [ ] The existing tests pass without changes: same IBANs, NIF validation, masking, minutes and `Retry-After`.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (English camelCase + `@DisplayName` in Spanish, after #166).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (`Retry-After` keeps its value and format).
- [ ] Any new business term is in `docs/dominio/glosario.md` (*BBAN*, if used).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Order with #166.** This plan goes after #166, so that `DurationsTests` already uses the new test naming convention and the PRs do not conflict on the test files.
- **`Durations` name and place.** Proposed in `adapter.in` because it is shared by web and REST. If the user prefers it close to its users, the alternative is one private method per class, which keeps the duplication between `LoginController` and `SignUpController`.
- **`CCC_MODULUS - 1`.** It avoids the magic `10`, but "10 becomes 1" is a rule of the CCC algorithm. If it reads worse than before, a small method `cccDigit(int remainder)` with the Javadoc of the rule is clearer than any constant.

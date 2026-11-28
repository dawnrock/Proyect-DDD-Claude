# Plan: `record` as the only data carrier outside the domain (remove Immutables)

- **Issue:** [#219](https://github.com/dawnrock/Proyect-DDD-Claude/issues/219)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-28
- **Status:** done
- **Total estimate:** ~95 min (11 tasks)

## Goal

The project uses three mechanisms for data objects: `record` (domain value objects and events, REST requests, `@ConfigurationProperties`), Immutables (`*Def` interfaces for the commands, queries and views of `application` and for the REST responses) and Lombok (`@Getter`/`@Setter` on web forms). Java 21 `record`s cover what Immutables gives here: immutability, `equals`/`hashCode`/`toString`, defaults and validation in the compact constructor, and Jackson 3 serialization without extra annotations. This refactor turns the 16 Immutables types into `record`s and removes Immutables from the build, so there is one way to write a data carrier.

**Must NOT change:** the behaviour of the application services, the validation messages, the JSON of the REST API (field names, nullability, `docs/api/openapi.yaml`) and the web pages.

### Out of scope

- Lombok: `@RequiredArgsConstructor` and `@Slf4j` stay (they are not data carriers), and so do `@Getter`/`@Setter` on the web forms, which Spring MVC binds as mutable JavaBeans (see open question 2).
- The domain: it already uses hand-written `record`s and classes.
- `Monolithic`: frozen at phase 1.

## Context

What Immutables does today, and its `record` equivalent:

| Immutables feature | Where | `record` equivalent |
| ------------------ | ----- | ------------------- |
| Builder (`X.builder()...build()`) | ~180 call sites, ~25 in `src/main` and ~155 in tests | Static factories named after the common cases, plus the canonical constructor for the rest (see "Replacing the builders") |
| Non-null attributes (NPE on `build()`) | Every attribute without `@Nullable` | `Objects.requireNonNull` in the compact constructor |
| `@Value.Default` | `AccountDetails`, the six commands | Compact constructor replaces `null` with the default |
| `@Value.Check validate()` | The six commands (#181: list of named checks) | Called from the compact constructor; the checks and messages stay the same |
| Immutable copies of lists | `AccountDetails.transactions()`, responses | `List.copyOf` in the compact constructor |
| Jackson 3 (`@JsonSerialize(as = …)`) | The five REST responses | Not needed: Jackson 3 serializes `record`s natively |
| Constants in the interface (`USERNAME_PATTERN`…) | `OpenAccountCommandDef`, `SignUpCommandDef` | `static final` fields of the `record` |

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.rest` | `AccountDetailsResponseDef`, `AccountSummaryResponseDef`, `TokenResponseDef`, `TransactionResponseDef`, `TransferResponseDef` | → `record` without the `Def` suffix |
| `application` | `AccountSummaryDef`, `TransactionSummaryDef`, `TransferSummaryDef`, `AccountDetailsDef`, `CallerDef` | → `record` |
| `application` | `DepositCommandDef`, `WithdrawCommandDef`, `TransferCommandDef`, `OpenAccountCommandDef`, `OpenAnotherAccountCommandDef`, `SignUpCommandDef` | → `record` with defaults and `validate()` in the compact constructor |
| `application` | `ApplicationValue.java` | Removed |
| tests | `ImmutablesSetupTests`, `JacksonImmutablesTests` | Removed / replaced by a `record` round-trip test |
| build | `pom.xml`, `checkstyle-suppressions.xml`, `spotbugs-exclude.xml` | Remove the Immutables dependency, the annotation processor and the exclusions of generated code |
| docs | `CLAUDE.md`, ADR 0003, new ADR 0028, `.claude/skills/*`, `.claude/agents/*`, `docs/arquitectura/fase-2-hexagonal.md`, `README.md` | "Data carriers are `record`s" |

### Replacing the builders

Decision of the user (2026-11-28): **static factories** for the common cases and the canonical constructor for everything else; no Lombok `@Builder` in production code. The factories come from grouping the ~180 current `builder()` calls by the attributes they set:

| Type | Current calls (attributes set) | Replacement |
| ---- | ------------------------------ | ----------- |
| `Caller` | 40 (`username`, `admin`; 22 `admin(true)`, 17 `admin(false)`) | `Caller.customer(username)`, `Caller.administrator(username)`; the existing `Caller.system()` stays. Removes a boolean parameter at every call site |
| `DepositCommand`, `WithdrawCommand` | 26 without `currency`, 8 with it | `DepositCommand.of(accountId, amount)` (EUR) and the canonical `new DepositCommand(accountId, amount, currency)` |
| `TransferCommand` | 12 without `currency`, 4 with it, 1 without target (validation test) | `TransferCommand.of(sourceAccountId, targetAccountNumber, amount)` (EUR) and the canonical constructor, which the validation test uses with `null` |
| `OpenAnotherAccountCommand` | 4 without `currency`, 2 with it | `OpenAnotherAccountCommand.of(initialDeposit)` and the canonical constructor |
| `OpenAccountCommand` | ~12 with the five holder fields, 3 with only `holderTaxId` + `initialDeposit`, the rest with `currency` | `OpenAccountCommand.forHolder(holderTaxId, initialDeposit)` (the rest by default), `OpenAccountCommand.of(holderName, holderTaxId, holderEmail, initialDeposit, ownerUsername)` (EUR) and the canonical constructor. The adapters' `requireNonNullElse(…, "")` go away: the compact constructor turns `null` into the default |
| `SignUpCommand` | 13 without `currency`, 2 with it | `SignUpCommand.of(fullName, taxId, email, username, initialDeposit)` (EUR) and the canonical constructor |
| `TransactionSummary` | 6 without counterpart, 3 with it | `TransactionSummary.of(id, type, amount, currency, occurredOn, status)` (no counterpart) and the canonical constructor with the `Optional<String>` counterpart |
| `AccountDetails` | 4 without tax id, e-mail or transactions; 4 with some of them | `AccountDetails.of(id, accountNumber, holderName, segment, balance, currency)` (empty tax id, e-mail and transactions) and the canonical constructor |
| `AccountSummary`, `TransferSummary` and the five REST responses | All calls set every attribute | Canonical constructor only: a factory would only repeat it |

Rules for the factories:

- They only fill in the documented defaults (EUR, empty text, empty list); validation always runs in the compact constructor, so a factory cannot build an invalid object.
- The name says the case (`forHolder`, `customer`), or `of` when it is "the usual fields"; no factory with the same parameters as the canonical constructor.
- Argument order follows the order of the `record` components, so each factory reads like the constructor minus the defaults.

## Tasks

Order: from the outside in (responses first, which nothing else depends on), then views, then commands, then the build. Every task leaves `./mvnw verify` green: a `record` keeps the generated type's name, so only the call sites that use `builder()` change.

### [x] T1 — Safety net for the JSON of the API (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/dawnrock/ddd/adapter/in/rest/RecordJsonTests.java` (new)
- **Depends on:** —
- **What to do:**
  1. Serialize one instance of each REST response with the application's `JsonMapper` and assert the exact JSON (field names and order, `null` fields).
- **Acceptance criteria:**
  - [x] The test passes against the current Immutables types, so it can detect any change in T2. The JSON is in **alphabetical order** (Jackson 3's default), not in declaration order: the test pins it.

### [x] T2 — REST responses as `record`s (~10 min)

- **Layer:** adapter.in
- **Files:** the five `*ResponseDef.java` in `adapter.in.rest` and their mapping in the controllers
- **Depends on:** T1
- **What to do:**
  1. Turn each one into a `record` named without `Def`, with `List.copyOf` for lists and `requireNonNull` where the attribute was mandatory.
  2. Replace `X.builder()…build()` with the constructor in the controllers.
- **Acceptance criteria:**
  - [x] T1 passes unchanged (only its construction syntax changed); `node scripts/contract-test.mjs` passes (738/738).
- **Found by T1:** a `record` is serialized in the order of its components (Jackson puts creator properties first, even with `@JsonPropertyOrder(alphabetic = true)`), while the Immutables types came out alphabetically. Each response `record` carries an explicit `@JsonPropertyOrder({...})` with its fields in alphabetical order, so the JSON stays byte-for-byte the same.

### [x] T3 — Views as `record`s: `AccountSummary`, `TransactionSummary`, `TransferSummary` (~10 min)

- **Layer:** application
- **Files:** the three `*Def.java`, their call sites in `application` and tests
- **Depends on:** —
- **What to do:**
  1. `record` with null checks; `TransactionSummary.of(…)` without counterpart (see "Replacing the builders").
  2. Update the call sites: `AccountSummary` and `TransferSummary` with the canonical constructor.
- **Acceptance criteria:**
  - [x] `./mvnw verify` green; no test changes beyond the construction syntax (a `String` counterpart is now wrapped in `Optional.of`).

### [x] T4 — `AccountDetails` and `Caller` as `record`s (~10 min)

- **Layer:** application
- **Files:** `AccountDetailsDef.java` (two defaults, list copy), `CallerDef.java`, call sites
- **Depends on:** T3
- **What to do:**
  1. `AccountDetails.of(…)` with empty tax id, e-mail and transactions.
  2. `Caller.customer(username)`, `Caller.administrator(username)` and `Caller.system()`; replace the 40 `builder().username(…).admin(…)` calls.
- **Acceptance criteria:**
  - [x] Defaults behave as before (`ApplicationRecordsTests`, proven by removing the tax id default: red); `./mvnw verify` green.
  - [x] No `admin(true)`/`admin(false)` left at call sites (`CurrentCaller` keeps `new Caller(name, admin)`: the flag comes from the authorities).

### [x] T5 — Money commands: `DepositCommand`, `WithdrawCommand`, `TransferCommand` (~10 min)

- **Layer:** application
- **Files:** the three `*Def.java`, call sites
- **Depends on:** T4
- **What to do:**
  1. Compact constructor: defaults, then `validate()` with the same named checks and messages (#181).
  2. Factories `DepositCommand.of`, `WithdrawCommand.of`, `TransferCommand.of` (EUR); the canonical constructor where a currency or an invalid value is passed.
- **Acceptance criteria:**
  - [x] The existing validation tests pass without changing their expected messages.
- **Deviation:** the test of a transfer without target account expected Immutables' `IllegalStateException` ("some of required attributes are not set"); the `record` throws `NullPointerException` with the attribute name, so the test now expects that exception and that message. The REST and web adapters lost their `if (currency != null)` branches: a `null` currency becomes `EUR` in the compact constructor (`CommandValidation.DEFAULT_CURRENCY`).

### [x] T6 — Account opening and sign-up commands (~15 min)

- **Layer:** application
- **Files:** `OpenAccountCommandDef.java`, `OpenAnotherAccountCommandDef.java`, `SignUpCommandDef.java`, call sites (adapters and tests)
- **Depends on:** T5
- **What to do:**
  1. Same as T5; the pattern and message constants become `static final` fields. Factories `OpenAccountCommand.forHolder` and `.of`, `OpenAnotherAccountCommand.of`, `SignUpCommand.of`; remove the adapters' `requireNonNullElse(…, "")`, now done by the compact constructor.
- **Acceptance criteria:**
  - [x] The validation tests and the web/REST sign-up and opening tests pass unchanged (expected messages and values untouched).
- **Deviation:** 29 test call sites used a builder as a *template* (a valid command whose test changes one field, or the "first failing rule" tests that fix fields one by one). Instead of repeating every argument of the canonical constructor, they use test builders in `src/test/.../support/CommandBuilders` (Lombok `@Builder` on static methods that call the `record` constructor; `CLAUDE.md` allows `@Builder` only in test builders, which is where it is). Complete chains became factories or the canonical constructor as planned. The adapters lost their `requireNonNullElse(…, "")` and `if (currency != null)`: the compact constructors do it.

### [x] T7 — Remove `ApplicationValue` and the Immutables setup tests (~5 min)

- **Layer:** application · tests
- **Files:** `ApplicationValue.java`, `ImmutablesSetupTests.java`, `JacksonImmutablesTests.java`
- **Depends on:** T2-T6
- **Acceptance criteria:**
  - [x] `grep -r "org.immutables" src` returns nothing (the JSON round trip of the responses is covered by `RecordJsonTests`; the request records were already records).

### [x] T8 — Remove Immutables from the build (~5 min)

- **Layer:** build
- **Files:** `pom.xml`, `checkstyle-suppressions.xml`, `spotbugs-exclude.xml`
- **Depends on:** T7
- **What to do:**
  1. Remove the `value-annotations` dependency, `immutables.version` and the `org.immutables:value` processor (Lombok stays the only one in `annotationProcessorPaths`).
  2. Remove the exclusions that only existed for generated code in `target/generated-sources`.
- **Acceptance criteria:**
  - [x] `./mvnw clean verify` green with the static analysis unchanged (no new PMD/SpotBugs/Checkstyle warnings). Removed: the dependency, `immutables.version`, the processor, the SpotBugs exclusion for `@org.immutables.value.Generated`, and the Checkstyle `sourceDirectories` / PMD `excludeRoots` that only kept generated code out (`checkstyle-suppressions.xml` had nothing for it).

### [x] T9 — ArchUnit: data carriers are `record`s (~5 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests.java`
- **Depends on:** T8
- **What to do:**
  1. Rule: classes in `application` named `*Command`, `*Query`, `*Summary`, `*Details` and in `adapter.in.rest` named `*Request`/`*Response` are `record`s. Validate it by sabotage (a class instead of a record → red).
  2. Remove `org.immutables` from the rules that mention it.
- **Acceptance criteria:**
  - [x] The rule fails with a non-record carrier (a temporary `application.SabotageQuery` class) and passes after reverting. It is one explicit predicate (`DATA_CARRIERS`): ArchUnit folds chained `.and()/.or()` from left to right, which would not express the intended condition. The only `org.immutables` mention in the rules was the D1 comment, cleaned in T7.

### [x] T10 — Documentation (~10 min)

- **Layer:** docs
- **Files:** new ADR 0028 (supersedes the Immutables part of ADR 0003), `docs/adr/README.md`, `CLAUDE.md` (Lombok/Immutables table and allowed dependencies; needs the user's approval), `.claude/skills/ddd-building-block/references/lombok-immutables.md`, `hexagonal-adapter`, `implement-plan(-team)`, `plan-feature`, `.claude/agents/revisor-ddd.md`, `docs/arquitectura/fase-2-hexagonal.md`, `README.md`
- **Depends on:** T9
- **Acceptance criteria:**
  - [x] No current document recommends Immutables (history, plans and frozen contracts keep their wording). `CLAUDE.md` and `.claude/` changed with the user's approval (2026-11-28); the skill reference is now `references/lombok-records.md`, the contract template names `record`s, and `revisor-ddd` checks the compact constructors.

### [x] T11 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md`
- **Depends on:** T10

## Global acceptance criteria

**Specific:**

- [x] No `org.immutables` in `pom.xml` or `src/`; no `*Def` types left.
- [x] The JSON of every REST response is byte-for-byte the same (T1) and `node scripts/contract-test.mjs` passes; `docs/api/openapi.yaml` does not change.
- [x] Validation messages and defaults behave as before (existing tests unchanged except for construction syntax).

**Fixed:**

- [x] All tasks and their criteria are checked.
- [x] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [x] The hexagonal architecture is respected, verified by ArchUnit.
- [x] The domain only depends on the JDK (rule D1, which also rejects Lombok and Immutables; ADR 0009 and 0026), verified by ArchUnit.
- [x] New tests follow `src/test/CLAUDE.md` (method name in English camelCase + `@DisplayName` in Spanish).
- [x] No dependencies added (one is removed).
- [x] API: n/a (the contract does not change).
- [x] Documentation written or changed by this plan is in English.
- [x] Project status updated.

## Risks and open questions

1. ~~Optional fields without a builder~~ **Decided (2026-11-28):** static factories plus the canonical constructor, as in "Replacing the builders". Lombok `@Builder` on the `record`s was discarded (it breaks "`@Builder` only in test builders").
2. **Web forms as `record`s.** Spring MVC can bind a form to a `record` through its constructor, but redisplaying the form with errors (`th:field`) needs JavaBean getters. Kept out of scope; if wanted, a spike in a separate issue.
3. **Null checks.** Immutables rejected `null` in every mandatory attribute; forgetting a `requireNonNull` in a `record` would accept it silently. T1 and the existing tests cover the API; review each compact constructor in `revisor-ddd`.
4. Most of the ~180 call sites are in tests, so the diff is large but mechanical.

# 0028 · `record` as the only data carrier; Immutables removed

- **Status:** accepted · 2026-11-28
- **Issue:** #219 (from the technical audit of 2026-11-28, PR #218) · **Supersedes:** the Immutables part of [ADR 0003](0003-lombok-immutables.md) · **Related:** [ADR 0009](0009-domain-without-lombok.md) and [ADR 0026](0026-domain-rules-in-archunit.md) (Lombok), [ADR 0014](0014-contract-versioning.md) (the API contract)

## Context

The project used three mechanisms for data objects: `record` (domain value objects and events, REST requests, `@ConfigurationProperties`), Immutables (16 `*Def` interfaces: the commands, queries and views of `application` and the REST responses, generated through a shared `@ApplicationValue` style) and Lombok (`@Getter`/`@Setter` on web forms). ADR 0003 chose Immutables to practise with it. With Java 21, `record`s cover everything Immutables gave here: immutability, `equals`/`hashCode`/`toString`, defaults and validation in the compact constructor, immutable copies of lists, and Jackson 3 serialization without `@JsonSerialize(as = …)`. Immutables added an annotation processor, generated sources that the static analysis had to skip, and a second way of writing the same thing.

## Decision

- **Every data carrier is a `record`.** Immutables leaves the build (dependency, processor, the SpotBugs exclusion and the Checkstyle/PMD settings for generated code). `HexagonalArchitectureTests.dataCarriersAreRecords` checks that the commands, queries, summaries and details of `application` and the requests and responses of `adapter.in.rest` are `record`s.
- **Compact constructors** do what Immutables did: `Objects.requireNonNull(x, "x")` for every mandatory component, `requireNonNullElse` for the defaults (`EUR` through `CommandValidation.DEFAULT_CURRENCY`, empty texts), `List.copyOf` for lists, then the `validate()` rules of #181 in the same order and with the same messages. The pattern and message constants (`USERNAME_PATTERN`, `TAX_ID_MESSAGE`…) are `static final` fields of the `record`s.
- **Construction** (decided by the user on 2026-11-28): **static factories** for the common cases and the canonical constructor for the rest, no Lombok `@Builder` in production code. Factories only fill in documented defaults: `Caller.customer(…)`/`administrator(…)`/`system()`, `DepositCommand.of(…)`, `WithdrawCommand.of(…)`, `TransferCommand.of(…)`, `OpenAccountCommand.forHolder(…)`/`of(…)`, `OpenAnotherAccountCommand.of(…)`, `SignUpCommand.of(…)`, `TransactionSummary.of(…)`, `AccountDetails.of(…)`. The adapters pass optional values straight to the constructor: a `null` currency becomes `EUR`, a `null` holder name becomes empty.
- **Tests that start from a valid command and change one field** use test builders in `src/test/.../support/CommandBuilders` (Lombok `@Builder` on static methods that call the `record` constructor): `CLAUDE.md` allows `@Builder` only in test builders.
- **The JSON of the API does not change.** Jackson 3 writes a `record` in the order of its components (creator properties first, even with `@JsonPropertyOrder(alphabetic = true)`), while the Immutables types were written alphabetically. Each response `record` lists its fields in alphabetical order in `@JsonPropertyOrder({...})`; `RecordJsonTests` pins the exact JSON and Schemathesis checks the contract.

## Alternatives

- **Keep Immutables**: a second mechanism and a processor for what the language already does.
- **Lombok `@Builder` on the `record`s**: keeps the builders, but breaks the rule "`@Builder` only in test builders" and brings Lombok into `application`'s public types.
- **Hand-written builders**: more code than the factories, for few call sites outside tests.

## Consequences

- One way to write a data carrier; one annotation processor (Lombok).
- A forgotten `requireNonNull` would accept `null` silently (Immutables rejected it by construction): `revisor-ddd` checks the compact constructors.
- A missing mandatory component now throws `NullPointerException` with the component's name, instead of Immutables' `IllegalStateException`.
- A new REST response `record` must declare its `@JsonPropertyOrder` to keep the API's alphabetical field order.

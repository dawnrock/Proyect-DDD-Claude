# 0003 · Where Lombok and Immutables are used

- **Status:** accepted · 2026-10-23 · the domain part (`@Getter` and `@EqualsAndHashCode` allowed in `domain..`) is superseded by [ADR 0009](0009-domain-without-lombok.md): the domain does not use Lombok

## Context

We want to practise with Lombok and Immutables, but the domain must protect its invariants and not depend on libraries (today checked by rule D1 of `LayeredArchitectureTests`, "the domain only depends on the JDK", since #12; `DomainIsolationTests` only forbids Spring and `jakarta`).

## Decision

- **Lombok** outside the domain for constructor injection (`@RequiredArgsConstructor`) and logging (`@Slf4j`); in the domain only `@Getter` with fluent accessors and `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`. `@Builder` only in test builders.
- **Immutables** for the commands, queries and views of `application` and for the adapters' DTOs. **Never** in `domain..`.
- The domain's value objects stay hand-written `record`s.
- `scripts/check-domain.mjs` (`ddd-building-block` skill) and CI check what ArchUnit cannot see (Lombok annotations with `SOURCE` retention). ArchUnit forbids `org.immutables` in the domain since phase 1 (rule D1, #12).

## Configuration

Implemented in issue [#2](https://github.com/dawnrock/Proyect-DDD-Claude/issues/2).

| Piece | Value | Where |
| ----- | ----- | ----- |
| Lombok | 1.18.46, version managed by Spring Boot 4.1.1; `optional` dependency, excluded from the executable JAR | [`pom.xml`](../../pom.xml) |
| Immutables | 2.12.3: `org.immutables:value-annotations` (`provided`) for the annotations and `org.immutables:value` only as a processor | [`pom.xml`](../../pom.xml) |
| Processors | `maven-compiler-plugin` → `annotationProcessorPaths` with Lombok and Immutables. Once they are declared, Maven stops looking for processors on the classpath, so both must be listed. They apply to `compile` and `testCompile` | [`pom.xml`](../../pom.xml) |
| Fluent accessors | `lombok.accessors.fluent = true` (`name()` instead of `getName()`), `config.stopBubbling = true` and `lombok.addLombokGeneratedAnnotation = true` | [`lombok.config`](../../lombok.config) |
| Immutables style | `@ApplicationValue` meta-annotation: the abstract type carries the `Def` suffix and the generated one takes the name without it (`OpenAccountCommandDef` → `OpenAccountCommand`), public, without `with*` methods | [`ApplicationValue.java`](../../src/main/java/com/pronoide/ddd/application/ApplicationValue.java) |

The `LombokSetupTests` and `ImmutablesSetupTests` tests (in `src/test/.../build`) prove that both processors generate code.

**IDE:** for the IDE to see the generated code, annotation processing must be enabled (IntelliJ: *Enable annotation processing*; Eclipse/STS: install the Lombok agent and enable *annotation processing* in the project). `./mvnw` does not need it.

## Risk resolved: Immutables with Jackson 3

Spring Boot 4.1 uses **Jackson 3** (`tools.jackson.databind` 3.1.5, with `com.fasterxml.jackson.annotation` 2.21). The question was whether the Immutables integration, designed for Jackson 2, works with it. Checked in issue [#8](https://github.com/dawnrock/Proyect-DDD-Claude/issues/8):

- **It works** with Immutables 2.12.3 if the abstract type is annotated with `@JsonSerialize(as = X.class)` and `@JsonDeserialize(as = X.class)` from **`tools.jackson.databind.annotation`** (not the `com.fasterxml.jackson.databind` ones, which are not on the classpath). The generated code adds `@JsonCreator` and `@JsonProperty(required = true)` per attribute.
- `JacksonImmutablesTests` proves it with a round trip to JSON. Without the annotations the same test fails with `InvalidDefinitionException` (validated by sabotage).

**Rule for the REST DTOs (#5 onwards):** they may be Immutables types with `@ApplicationValue`, `@Value.Immutable` and the two Jackson 3 annotations:

```java
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = OpenAccountRequest.class)
@JsonDeserialize(as = OpenAccountRequest.class)
interface OpenAccountRequestDef {
    String holderName();
    BigDecimal initialDeposit();
}
```

If a DTO needs Bean Validation (`@Valid`) and that is awkward with Immutables, a `record` is allowed, justified in the feature's contract.

## Consequences

- Less boilerplate in services, adapters and DTOs without opening the door to setters in the aggregates.
- Two annotation processors in the build (`annotationProcessorPaths`).

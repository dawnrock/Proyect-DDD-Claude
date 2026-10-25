# Plan: ArchUnit rules for the layered architecture

- **Issue:** [#12](https://github.com/dawnrock/Proyect-DDD-Claude/issues/12)
- **Type:** feature (architecture tests; label `tipo:test`)
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-25
- **Approved:** by the user on 2026-10-25
- **Status:** in progress
- **Total estimate:** ~45 min (6 tasks)

## Goal

Verify the layered architecture automatically (course chapter 18.1). A new `LayeredArchitectureTests` turns the dependency table D1-D5 of `docs/arquitectura/fase-1-capas.md` into ArchUnit rules, plus the two extra rules the issue asks for: no public `set*` methods in the domain, and controllers never access the repository. Every rule must pass on the current code and is validated by breaking it temporarily (red), then reverting (green).

No production code changes: the current code already follows D1-D5 (checked while planning: presentation only imports `DomainException` from the domain, nothing in presentation imports persistence, web and REST do not know each other, the domain imports only the JDK, and there are no public setters in the domain).

### Out of scope

- Changing production code (if a rule fails on the current code, stop and ask: that would be a real violation to fix in its own task).
- `DomainIsolationTests` stays as it is (framework-free domain, private state). Its stale reference to `com.pronoide.ddd.bootstrap..` (a package that does not exist) is noted as a pending item, not fixed here.
- Hexagonal rules (`HexagonalArchitectureTests`, phase 2).

## Context

- **ArchUnit 1.5.0** is already a test dependency; the rules follow `DomainIsolationTests`: `@AnalyzeClasses(packages = "com.pronoide.ddd", importOptions = ImportOption.DoNotIncludeTests.class)`, `@ArchTest static final ArchRule` fields with Spanish `snake_case` names (they are the test names). Test classes (such as `presentation/rest/support/FailingTestController`) are excluded.
- **Layers** (`layeredArchitecture().consideringOnlyDependenciesInLayers()`): `Presentation` = `..presentation..`, `Application` = `..application..`, `Domain` = `..domain..`, `Persistence` = `..persistence..`. `DddLabApplication` (root package) is in no layer. Immutables-generated classes (`Immutable*`) live in the same packages as their `*Def` and are covered by the same rules.
- **Rules → D-table:**

  | Rule | Covers | How |
  | ---- | ------ | --- |
  | `las_capas_solo_dependen_hacia_abajo` | D2, D3 and "nothing depends on presentation or application from below" | `layeredArchitecture()`: `Presentation` may not be accessed by any layer; `Application` only by `Presentation`; `Persistence` only by `Application`; `Domain` by `Application`, `Persistence` and `Presentation` |
  | `el_dominio_solo_depende_del_jdk` | D1 (includes Spring, `jakarta`, `org.immutables`) | `classes()` in `..domain..` `onlyDependOnClassesThat()` reside in `java..` or `..domain..` (plus primitives/arrays, see Risks) |
  | `la_presentacion_solo_conoce_las_excepciones_del_dominio` | D4 | `noClasses()` in `..presentation..` depend on classes in `..domain..` that are **not** assignable to `DomainException` (this also forbids `..domain.service..`) |
  | `la_web_y_rest_no_se_conocen` | D5 | two `noClasses()` rules (web → rest, rest → web), or `slices()` over `..presentation.(*)..` with `notDependOnEachOther()` |
  | `el_dominio_no_tiene_setters_publicos` | issue | `noMethods()` declared in `..domain..`, public, named `set[A-Z].*` |
  | `los_controladores_no_acceden_al_repositorio` | issue (and D4) | `noClasses()` meta-annotated with `@Controller` (covers `@RestController`) depend on `..persistence..` |

- **Validation by breaking** (`src/test/CLAUDE.md`): each task introduces a temporary forbidden dependency in production code, runs `./mvnw test -Dtest=LayeredArchitectureTests`, sees the expected rule fail, reverts, and notes it in the commit body and the PR.

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| tests (root) | `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java` | New: 6 rules |
| `docs` | `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md` | D1-D5 now enforced; status |

## Tasks

### [x] T1 — Layered architecture rule (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java`
- **Depends on:** —
- **What to do:**
  1. Create the class and `las_capas_solo_dependen_hacia_abajo` with `layeredArchitecture()` as in the table.
  2. Break it: a temporary field of type `CheckingAccountService` in `InMemoryCheckingAccountRepository` (persistence → application) → red; revert → green.
- **Acceptance criteria:**
  - [x] Rule green on the current code; red with the temporary dependency.

### [ ] T2 — D1: the domain only depends on the JDK (~5 min)

- **Layer:** tests
- **Files:** `LayeredArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `el_dominio_solo_depende_del_jdk`.
  2. Break it: a temporary `org.springframework.util.Assert` call (or an `org.immutables` annotation) in a domain class → red; revert.
- **Acceptance criteria:**
  - [ ] Rule green; red with the temporary dependency. `check-domain.mjs` still without errors.

### [ ] T3 — D4: presentation only knows the domain's exceptions (~10 min)

- **Layer:** tests
- **Files:** `LayeredArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `la_presentacion_solo_conoce_las_excepciones_del_dominio`.
  2. Break it twice: a temporary `MonetaryAmount` in `AccountController` → red; a temporary `MoneyTransferService` in `AccountWebController` → red; revert. `DomainException` and its subclasses stay allowed (the existing `catch (DomainException …)` keeps it green).
- **Acceptance criteria:**
  - [ ] Rule green; red in both temporary cases.

### [ ] T4 — D5: web and REST do not know each other (~5 min)

- **Layer:** tests
- **Files:** `LayeredArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `la_web_y_rest_no_se_conocen` (both directions).
  2. Break it: a temporary reference to `TransferRequest` from `AccountWebController` → red; revert.
- **Acceptance criteria:**
  - [ ] Rule green; red with the temporary dependency.

### [ ] T5 — No public setters in the domain; controllers do not access the repository (~10 min)

- **Layer:** tests
- **Files:** `LayeredArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `el_dominio_no_tiene_setters_publicos` and `los_controladores_no_acceden_al_repositorio`.
  2. Break each: a temporary `public void setCurrentBalance(...)` in `CheckingAccount` → red; a temporary `InMemoryCheckingAccountRepository` field in `TransferController` → red (the layered rule from T1 also fails; that is expected); revert.
- **Acceptance criteria:**
  - [ ] Both rules green; each red with its temporary change.

### [ ] T6 — Documentation (~5 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T2-T5
- **What to do:**
  1. `fase-1-capas.md`: "Implementado en la #12" (each D-row → its rule name; the two extra rules).
  2. Project status: #12 done, next step `/plan-feature 13`, pending item about the stale `bootstrap` package in `DomainIsolationTests`, history row.
- **Acceptance criteria:**
  - [ ] Documents updated; additions to documents still in Spanish stay in Spanish until they are translated.

## Global acceptance criteria

**Specific:**

- [ ] `LayeredArchitectureTests` has the layered rule plus D1, D4, D5, no public setters and controllers without repository access; all green on the current code.
- [ ] Every rule was validated by breaking it temporarily (listed in the PR).
- [ ] No production code changed.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The layered architecture is respected, now verified by ArchUnit (`LayeredArchitectureTests`) and by `revisor-ddd`.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use `@Data`, `@Setter`, `@Builder` or `@AllArgsConstructor` (`check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` names in Spanish; ArchUnit rules are `@ArchTest` fields, as in `DomainIsolationTests`).
- [ ] No dependencies added beyond the planned ones (ArchUnit is already there).
- [ ] If the API changes: OpenAPI — **n/a** (no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md` — n/a (no business terms).
- [ ] Documentation written or changed by this plan is in English (additions to documents still in Spanish excepted).
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- **"Only the JDK" and primitives/arrays:** `onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..")` may flag primitive or array types (empty package). If it does, add `""` to the packages or use a predicate that accepts primitives and arrays of allowed types; decided in T2.
- **Overlap between rules:** the layered rule already forbids presentation → persistence; `los_controladores_no_acceden_al_repositorio` is kept because the issue asks for it explicitly and its failure message is clearer. A single forbidden dependency can therefore fail two rules.
- **Generated code:** Immutables classes are generated into `application` and `presentation.rest`; if a generated class ever broke a rule (it should not: they only use their own package and the JDK/Jackson), it would be excluded explicitly and noted.

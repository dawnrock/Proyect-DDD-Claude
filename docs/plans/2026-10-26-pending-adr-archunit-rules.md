# Plan: Pending ArchUnit rules from the ADRs and DomainIsolationTests cleanup

- **Issue:** [#54](https://github.com/dawnrock/Proyect-DDD-Claude/issues/54)
- **Type:** refactor
- **Phase:** 1 · Layered monolith
- **Base branch:** `Monolithic`
- **Date:** 2026-10-26
- **Status:** in progress
- **Total estimate:** ~30 min (4 tasks)

## Goal

Turn two architecture rules that today only live in ADRs into executable ArchUnit rules, and remove a leftover from `DomainIsolationTests`. ADR 0008 keeps account ownership in the application layer with a plain `Caller` (no Spring Security, no method security), and ADR 0004 keeps phase 1 free of port interfaces in `persistence`. Both hold today only because nobody has broken them. No production code changes: the current behaviour must stay exactly the same and every existing test must keep passing.

### Out of scope

- Checking "`@Builder` only in test builders" (ADR 0003): ArchUnit cannot see Lombok annotations (`SOURCE` retention) and looking for `*Builder` classes would flag the builders Immutables generates (`OpenAccountCommand.Builder`). `revisor-ddd` keeps watching it.
- Removing the framework rule of `DomainIsolationTests` although D1 covers it in phase 1: it is the domain-isolation rule that survives into phase 2, when `LayeredArchitectureTests` is replaced.
- `HexagonalArchitectureTests` (phase 2): it will have to carry the ADR 0008 rule over; noted in the risks.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| tests (ArchUnit) | `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java` | Two new rules: `application` without Spring Security / method security (ADR 0008) and no interfaces in `persistence` (ADR 0004) |
| tests (ArchUnit) | `src/test/java/com/pronoide/ddd/DomainIsolationTests.java` | Drop the non-existent `com.pronoide.ddd.bootstrap..` package |
| docs | `docs/arquitectura/fase-1-capas.md` | Add the new rules to the table of rules implemented in #12 |
| docs | `docs/ia/estado-del-proyecto.md` | Issue status, next step (#13) and history row; remove the "bootstrap" pending item |

Facts checked on the current code:

- `org.springframework.security` is only used in `presentation` (`CurrentCaller`, `SecuritySupport`, both security configs, controllers and web advices). `application` does not import it.
- There is no `@EnableMethodSecurity`, `@PreAuthorize`, `@PostAuthorize` or `@Secured` anywhere in `src/main`.
- `persistence` has two concrete classes (`InMemoryCheckingAccountRepository`, `InMemoryAccountOwnershipRepository`) and no interfaces.

Technical decisions:

- **Method security covers the whole family**, not only `@PreAuthorize`: `@PreAuthorize`, `@PostAuthorize` and `@Secured` (by fully qualified name, so the test does not need extra imports). Any of them would move the ownership rule into the framework, which is what ADR 0008 rejects.
- **The method-security rule applies to every layer**, not only `application`: putting `@PreAuthorize` on a controller would equally bypass the `Caller` rule.
- Both rules go in `LayeredArchitectureTests`, next to the D1-D5 rules, with a Javadoc that cites their ADR, like the existing rules.
- `fase-1-capas.md` is still in Spanish (pending translation issue); the new rows are written in Spanish so the table does not mix languages, as previous additions to that document did.

## Tasks

### [x] T1 — Rule: application and method security without Spring Security (ADR 0008) (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java`
- **Depends on:** —
- **What to do:**
  1. Add `la_aplicacion_no_depende_de_spring_security`: `noClasses().that().resideInAPackage("..application..").should().dependOnClassesThat().resideInAPackage("org.springframework.security..")`, with Javadoc citing ADR 0008.
  2. Add `nadie_usa_la_seguridad_por_metodos`: no methods and no classes annotated with `org.springframework.security.access.prepost.PreAuthorize`, `...PostAuthorize` or `org.springframework.security.access.annotation.Secured` (by name).
  3. Run `./mvnw test -Dtest=LayeredArchitectureTests`: green.
  4. Validate by breaking: temporarily import an `org.springframework.security` class in `CheckingAccountService` and put `@PreAuthorize("hasRole('ADMIN')")` on one method; each rule must fail with its own message. Revert.
- **Acceptance criteria:**
  - [x] Both rules pass on the current code.
  - [x] Each rule failed when broken on purpose (noted in the commit).

### [x] T2 — Rule: no port interfaces in persistence (ADR 0004) (~5 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java`
- **Depends on:** —
- **What to do:**
  1. Add `la_persistencia_no_tiene_interfaces`: `noClasses().that().resideInAPackage("..persistence..").should().beInterfaces()`, with Javadoc citing ADR 0004 and stating that it is a phase 1 rule (phase 2 declares the port in `domain.model`).
  2. Validate by breaking: add a temporary `interface CheckingAccountRepository` in `persistence`; the rule must fail. Remove it (and its compiled class from `target/`).
- **Acceptance criteria:**
  - [x] The rule passes on the current code and failed when broken on purpose.

### [x] T3 — Remove the non-existent bootstrap package from DomainIsolationTests (~5 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/DomainIsolationTests.java`
- **Depends on:** —
- **What to do:**
  1. Remove `"com.pronoide.ddd.bootstrap.."` from `el_dominio_no_depende_del_framework`; keep Spring and `jakarta`, and the private-state rule.
  2. Run `./mvnw test -Dtest=DomainIsolationTests`: green.
- **Acceptance criteria:**
  - [x] `DomainIsolationTests` no longer mentions `bootstrap` and passes.

### [ ] T4 — Document the new rules and update the project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-1-capas.md`, `docs/ia/estado-del-proyecto.md`
- **Depends on:** T1, T2, T3
- **What to do:**
  1. In `fase-1-capas.md`, after the table of #12, add the three new rules (two from T1, one from T2) with the ADR each one enforces, marked as implemented in #54.
  2. In `estado-del-proyecto.md`: #54 done, next step `/plan-feature 13`, remove the "bootstrap" pending item, history row; note that phase 2 must carry the ADR 0008 rules into `HexagonalArchitectureTests`.
- **Acceptance criteria:**
  - [ ] The rules table lists every `@ArchTest` of `LayeredArchitectureTests`.
  - [ ] `node scripts/check-project-status.mjs` accepts the PR's file list.

## Global acceptance criteria

**Specific:**

- [ ] Three new ArchUnit rules, each validated by breaking it (stated in the PR).
- [ ] `DomainIsolationTests` without `bootstrap`.
- [ ] No production code changed (`git diff origin/Monolithic -- src/main` is empty).

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (layers: each layer only uses the one below), verified by ArchUnit.
- [ ] The domain does not depend on Spring, `jakarta` or `org.immutables`, and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + Spanish names for the `@ArchTest` fields, as the existing rules).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no API changes).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no new terms).
- [ ] Documentation written or changed by this plan is in English, except the rows added to `fase-1-capas.md`, which follow that document's current language (see Context).
- [ ] `docs/ia/estado-del-proyecto.md` updated: issue status, next step, new pending items and history row.

## Risks and open questions

- **Phase 2:** `LayeredArchitectureTests` is replaced by `HexagonalArchitectureTests`. The ADR 0008 rules (application without Spring Security, no method security) must be carried over; the "no interfaces in persistence" rule must not, because phase 2 introduces ports. Recorded in the project status (T4).
- **Rule depends on PR #53?** No: this plan only touches tests and docs that PR #53 does not change, except `estado-del-proyecto.md`. If #53 is merged first (expected), T4 starts from its version of that file.
- **Template drift found while planning:** the fixed criterion in `.claude/skills/plan-feature/assets/template.md` still lists `@Data`, `@Setter`, `@Builder`, `@AllArgsConstructor` instead of "no Lombok in the domain" (ADR 0009). This plan uses the new wording; the template itself is not part of #54.

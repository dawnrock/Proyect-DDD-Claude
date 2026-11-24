# Plan: English camelCase test names, Spanish @DisplayName

- **Issue:** [#166](https://github.com/dawnrock/Proyect-DDD-Claude/issues/166)
- **Type:** refactor
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-24
- **Status:** in progress
- **Total estimate:** ~130 min (15 tasks)

## Goal

Test methods are named in Spanish `snake_case` (792 methods in 79 files) and repeat their Spanish `@DisplayName`, while `CLAUDE.md` says code goes in English. The Clean Code course (ch. 1 and 21) asks for one language across the project and names tests in English camelCase, with the natural-language sentence in `@DisplayName`. The user adopted that convention on 2026-11-24.

- **Improves:** test method names in English camelCase describing the behaviour. The 21 `@ArchTest` fields with Spanish names become English camelCase `@ArchTest` methods. `src/test/CLAUDE.md` and the skill templates describe the new convention. Checkstyle's naming rules (`MethodName`, `ConstantName`) can later run on `src/test` with no exceptions (#168).
- **Must NOT change:** the body of any test, any `@DisplayName`, the number of tests run, or what each ArchUnit rule checks.

### Out of scope

- Adding Checkstyle, PMD or SpotBugs to the build (#168). Here Checkstyle is only run locally, in a scratch copy of the project, as a check.
- Magic numbers in tests: they stay as test data (#167 and #168 apply `MagicNumber` to `src/main` only).
- Old plans in `docs/plans/` and `docs/analysis/`, which keep the names of their time.
- `Monolithic`, frozen.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| tests · `domain.model`, `domain.service` | 16 files, 147 methods | Rename methods |
| tests · `application`, `application.listener` | 15 files, 158 methods | Rename methods |
| tests · `adapter.in` (support: login attempts, sign-up limit, properties) | 11 files, 86 methods | Rename methods |
| tests · `adapter.in.web` | 10 files, 202 methods | Rename methods |
| tests · `adapter.in.rest` | 8 files, 111 methods | Rename methods |
| tests · `adapter.in.cli`, `adapter.in.scheduler`, `adapter.out.*` | 13 files, 32 methods | Rename methods |
| tests · root package, `config`, `build`, `support` | 8 files, 56 methods | Rename methods |
| tests · ArchUnit | `HexagonalArchitectureTests` (18), `DomainIsolationTests` (2), `AdapterTestIsolationTests` (1) | `static final ArchRule` fields with Spanish names → `@ArchTest` methods |
| docs · glossary | `docs/dominio/glosario.md` (61 references `Class#method` in the business rules table, R1-R32), `CLAUDE.md` (example `-Dtest='CheckingAccountTests#nombre*'`) | Point to the new method names |
| docs · conventions | `src/test/CLAUDE.md`, `.claude/skills/ddd-building-block/templates/BuildingBlockTests.java.template`, `.claude/skills/plan-feature/assets/template.md` | New convention (approved by the user in the issue) |

**Naming rule.** The method name states the behaviour of its `@DisplayName` in English: what happens, in which situation, and what is expected (`aRetailCustomerCannotOverdrawTheAccount`, `rejectsADepositWithANegativeAmount`). It does not translate word for word, and it does not need to repeat the class under test. Within a `@Nested` class, the name continues the nested class's sentence. Domain terms come from `docs/dominio/glosario.md` (for example *checking account*, *overdraft*, *segment*).

**ArchUnit.** ArchUnit 1.5 supports `@ArchTest static void name(JavaClasses classes)`. Each rule keeps its `ArchRule` definition, `because(...)` included, and is checked from a method named like the rest of the tests. The private helper constants (`ADAPTER_IN_SUPPORT`, `PORT_PACKAGES`) are already in `UPPER_SNAKE_CASE`. See the open questions for the alternative.

**How to check that nothing else changed.** Surefire writes one XML report per class in `target/surefire-reports`. Before T1 the per-class test counts are saved, and after every task they are compared with the current ones. Because `@DisplayName` does not change, the names shown in the reports stay the same too.

## Tasks

### [x] T1 — Baseline of the test suite (~5 min)

- **Layer:** tests
- **Files:** — (scratchpad only)
- **Depends on:** —
- **What to do:**
  1. `./mvnw verify` and save the per-class test count from `target/surefire-reports/*.xml` (`tests`, `skipped`) and the total.
  2. Run Checkstyle locally in a scratch copy with the course rules (`MethodName`, `ConstantName`) and save the count: 792 + 21.
- **Acceptance criteria:**
  - [x] Baseline saved: total tests and per-class counts (84 classes, 1,023 test cases, 0 skipped; Checkstyle 792 `MethodName` + 21 `ConstantName`). *Correction during T10:* the first count (1,004) read the `tests` attribute of the surefire report, which merges test cases that share a name across the `@Nested` classes of a file; as the rename made more names coincide, that attribute dropped while the cases did not. The baseline was rebuilt by counting `<testcase>` elements on `origin/Hexagonal` (1,023), and T3-T10 were checked against it class by class.

### [x] T2 — New convention in `src/test/CLAUDE.md` and the templates (~5 min)

- **Layer:** docs
- **Files:** `src/test/CLAUDE.md`, `.claude/skills/ddd-building-block/templates/BuildingBlockTests.java.template`, `.claude/skills/plan-feature/assets/template.md`
- **Depends on:** T1
- **What to do:**
  1. `src/test/CLAUDE.md`: method in English camelCase describing the behaviour, plus `@DisplayName` with the Spanish sentence, with the course-style example. ArchUnit rules are `@ArchTest` methods.
  2. Template `{{frase_en_snake_case}}` → a camelCase English placeholder, and the fixed criterion of `plan-feature` ("`snake_case` + `@DisplayName` in Spanish" → "English camelCase + `@DisplayName` in Spanish").
  3. Check that the agents (`backend-dev`, `web-dev`, `revisor-ddd`) and the other skills do not repeat the old convention (a grep found none on 2026-11-24).
- **Acceptance criteria:**
  - [x] `grep -rn "snake_case" .claude src/test/CLAUDE.md` only finds references to `UPPER_SNAKE_CASE` constants (none left; agents and other skills did not mention the convention).

### [x] T3 — `domain.model` (~10 min)

- **Layer:** tests
- **Files:** 14 files in `src/test/java/com/dawnrock/ddd/domain/model`, 125 methods
- **Depends on:** T2
- **What to do:**
  1. Rename each method following the naming rule. Do not change bodies or `@DisplayName`s.
- **Acceptance criteria:**
  - [x] No `void [a-z]+_` method left in the package; per-class counts match the baseline; `./mvnw verify` green. (125 renamed; 84 classes, 1,004 tests, same as the baseline)

### [x] T4 — `domain.service` (~5 min)

- **Layer:** tests
- **Files:** 2 files in `.../domain/service`, 22 methods
- **Depends on:** T2
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for the package. (22 renamed; 1,004 tests, same as the baseline)

### [x] T5 — `application` (~10 min)

- **Layer:** tests
- **Files:** 13 files in `.../application`, 152 methods
- **Depends on:** T2
- **What to do:**
  1. Same as T3. `CheckingAccountServiceTests` has the most methods. If the task goes over 10 minutes, split it into its own commit.
- **Acceptance criteria:**
  - [x] Same as T3, for the package. (152 renamed; 1,004 tests, same as the baseline)

### [x] T6 — `application.listener` and `adapter.out.*` (~5 min)

- **Layer:** tests
- **Files:** 2 files in `.../application/listener` (6 methods); `adapter/out/persistence` (8 files, 15), `adapter/out/event` (2, 3), `adapter/out/taxagency` (1, 2)
- **Depends on:** T2
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for the packages. (26 renamed; 1,004 tests, same as the baseline)

### [x] T7 — `adapter.in` support classes (~10 min)

- **Layer:** tests
- **Files:** 11 files directly in `.../adapter/in`, 86 methods
- **Depends on:** T2
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for the package. (86 renamed; 1,004 tests, same as the baseline)

### [x] T8 — `adapter.in.web`, first half (~10 min)

- **Layer:** tests
- **Files:** the 5 files with the most methods in `.../adapter/in/web` (about 100 of 202). *Deviation:* `AccountWebControllerTests` alone has 100 of the 202, so T8 is that file and T9 the other nine.
- **Depends on:** T2
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for those files. (100 renamed in `AccountWebControllerTests`; 1,004 tests, same as the baseline)

### [x] T9 — `adapter.in.web`, second half (~10 min)

- **Layer:** tests
- **Files:** the other 5 files in `.../adapter/in/web`
- **Depends on:** T8
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for the whole package. (102 renamed in the other nine files; the whole package is done; 1,004 tests, same as the baseline)

### [x] T10 — `adapter.in.rest` (~10 min)

- **Layer:** tests
- **Files:** 8 files in `.../adapter/in/rest`, 111 methods
- **Depends on:** T2
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for the package. (111 renamed; 1,023 test cases, class by class the same as the baseline)

### [x] T11 — `adapter.in.cli` and `adapter.in.scheduler` (~5 min)

- **Layer:** tests
- **Files:** `.../adapter/in/cli` (1 file, 11 methods), `.../adapter/in/scheduler` (1 file, 1 method)
- **Depends on:** T2
- **What to do:**
  1. Same as T3.
- **Acceptance criteria:**
  - [x] Same as T3, for the packages. (12 renamed; 1,023 test cases, class by class the same as the baseline)

### [x] T12 — Root package, `config`, `build` and `support` (~10 min)

- **Layer:** tests
- **Files:** root package (4 files, 44 methods: `DemoProfileTests`, `DddLabApplicationTests`…), `config` (1, 5), `build` (2, 5), `support` (1, 2)
- **Depends on:** T2
- **What to do:**
  1. Same as T3. The ArchUnit fields are T13.
- **Acceptance criteria:**
  - [x] Same as T3, for the packages. (56 renamed; no snake_case test method left in src/test; 1,023 test cases, class by class the same as the baseline)

### [x] T13 — ArchUnit rules as English `@ArchTest` methods (~10 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests`, `DomainIsolationTests`, `AdapterTestIsolationTests`
- **Depends on:** T2
- **What to do:**
  1. Each `@ArchTest static final ArchRule las_…` becomes `@ArchTest static void englishName(JavaClasses classes)`, which builds the same rule (same `because(...)`) and calls `check(classes)`. Shared definitions stay as private constants in `UPPER_SNAKE_CASE`.
  2. Validate by breaking one rule, as `src/test/CLAUDE.md` asks: add a forbidden dependency temporarily, check that `./mvnw test -Dtest=HexagonalArchitectureTests` fails on that rule, and revert. Record it in the commit.
- **Acceptance criteria:**
  - [x] The three classes run the same number of rules as in the baseline (18 + 2 + 1) and all pass.
  - [x] A broken rule makes its method fail. (21 rules turned into methods; sabotage: a Spring dependency added temporarily to `TaxId` made `theCoreDoesNotDependOnSpring`, `theDomainOnlyDependsOnTheJdk` and `theDomainDoesNotDependOnTheFramework` fail, then reverted)

### [x] T14 — Test references in the glossary and `CLAUDE.md` (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md`, `CLAUDE.md`
- **Depends on:** T3-T13
- **What to do:**
  1. Replace the 61 `Class#method` references in the business rules table (R1-R32) with the new names.
  2. Check them with a one-off command: every `Class#method` in the glossary exists as a method in `src/test/java`.
  3. `CLAUDE.md`: the single-method example (`-Dtest='CheckingAccountTests#nombre*'`) with an English camelCase prefix.
- **Acceptance criteria:**
  - [x] Every `Class#method` in the glossary resolves to an existing test method. (62 distinct references, including 2 to `*Contract` interfaces, all resolve; the `CLAUDE.md` example `CheckingAccountTests#aRetail*` was run)

### [x] T15 — Final check and project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `docs/ia/decisiones.md`, `docs/ia/historial.md`
- **Depends on:** T14
- **What to do:**
  1. `./mvnw verify` and compare the total and per-class counts with T1.
  2. Run Checkstyle again in the scratch copy: 0 `MethodName` and 0 `ConstantName`.
  3. `decisiones.md`: test naming convention (English camelCase + Spanish `@DisplayName`, ArchUnit rules as methods, #166). Project status and a row in the history.
- **Acceptance criteria:**
  - [x] Same number of tests as the baseline; Checkstyle naming rules give 0 warnings in `src/test`. (1,023 test cases, class by class the same as `origin/Hexagonal`; Checkstyle on a copy: 0 `MethodName`, 0 `ConstantName`, only the 267 `MagicNumber` left for #167 and #168)

## Global acceptance criteria

**Specific:**

- [ ] No test method in `snake_case` or in Spanish; every `@DisplayName` is unchanged (`git diff` shows no changed `@DisplayName` line).
- [ ] `./mvnw verify` runs the same number of tests as before, all green (JUnit + ArchUnit).
- [ ] ArchUnit rules still fail when broken (checked by breaking one).
- [ ] `src/test/CLAUDE.md` and the skill templates describe the new convention.
- [ ] The glossary's test references (R1-R32) point to existing methods.
- [ ] One commit per package (or pair of small packages), so the PR can be reviewed.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (English camelCase + `@DisplayName` in Spanish, as updated by T2).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (only tests and docs change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (none expected).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **ArchUnit rules: methods or constants?** The plan turns them into `@ArchTest` methods, which read like the rest of the tests. The cost is a little more code per rule (method + `check`). The alternative keeps the declarative fields and only renames them to English `UPPER_SNAKE_CASE` (`DEPENDENCIES_POINT_TOWARDS_THE_HEXAGON`). That is less code, but the reports show the rule names in upper case. Methods were proposed to the user in the conversation; confirm before T13.
- **Diff size and conflicts.** The PR touches almost every test file. It should go in when no other branch is open on the tests (today #165 is open but not started), and before #167, so that its new tests already use the new convention.
- **Translation quality.** About 790 names are translated by hand. Each commit is limited to one package so the review can check that each name still says what its `@DisplayName` says.
- **Duplicate names.** Two Spanish names may translate to the same English one within a class. That does not compile, so it cannot slip through, but it needs a more precise name.

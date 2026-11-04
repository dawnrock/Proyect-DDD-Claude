# Plan: ArchUnit rules for the hexagonal architecture

- **Issue:** [#22](https://github.com/dawnrock/Proyect-DDD-Claude/issues/22)
- **Type:** refactor (tests only)
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-04
- **Status:** in progress
- **Total estimate:** ~50 min (5 tasks)

## Goal

Check automatically that every dependency points into the hexagon (Lab 27.10). `HexagonalArchitectureTests` replaces `LayeredArchitectureTests`, which has been patched issue by issue since #14 and still describes layers. Its backbone is ArchUnit's `onionArchitecture()` (domain model, domain services, application services and one adapter per technology), plus explicit rules for the ports, Spring and security. Every rule is validated by breaking it.

Production code does not change; no rule that guards today is lost (mapping table below).

### Out of scope

- `DomainIsolationTests` and `AdapterTestIsolationTests` stay as they are: the first one survives both phases (#54) and the second one checks test code (#21).
- The phase-1 rules live on in the `Monolithic` branch (tag `v1-monolito`); `docs/arquitectura/fase-1-capas.md` keeps describing them there.
- Issue #23 (whatever closes phase 2).

## Context

`onionArchitecture()` components:

| Component | Packages |
| --------- | -------- |
| Domain models | `..domain.model..` (model and output ports) |
| Domain services | `..domain.service..` |
| Application services | `..application..` (services, input ports, the ownership output port, listeners) |
| Adapters | `cli` → `..adapter.in.cli..`, `rest` → `..adapter.in.rest..`, `web` → `..adapter.in.web..`, `persistence` → `..adapter.out.persistence..`, `event` → `..adapter.out.event..`, `taxagency` → `..adapter.out.taxagency..` |

What the onion checks on its own: the domain model does not depend on services, application or adapters; domain services do not depend on application or adapters; the application does not depend on adapters; **adapters do not depend on each other** (issue rule). Outside the components: the shared security support in the root of `adapter.in` (used by `rest` and `web`), `config` and the boot class; explicit rules cover them.

**Rule mapping** (nothing guarded today is dropped):

| `LayeredArchitectureTests` (today) | `HexagonalArchitectureTests` |
| ---------------------------------- | ---------------------------- |
| `las_capas_solo_dependen_hacia_abajo` | `las_dependencias_apuntan_hacia_el_hexagono` (onion) + `nadie_depende_de_config` |
| `el_dominio_solo_depende_del_jdk` (D1) | kept: `el_dominio_solo_depende_del_jdk` (covers `org.immutables`, issue rule) |
| `la_presentacion_solo_conoce_las_excepciones_del_dominio` (D4) | kept, renamed `los_adaptadores_de_entrada_solo_conocen_las_excepciones_del_dominio` |
| `la_web_y_rest_no_se_conocen` (D5), `los_adaptadores_de_salida_no_se_conocen`, `los_controladores_no_acceden_al_repositorio` | onion: adapters do not depend on each other |
| `los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada` | kept |
| `solo_adapter_out_conoce_los_adaptadores_de_salida` | onion + kept for `config` and the classes outside the components |
| `el_dominio_no_tiene_setters_publicos` | kept |
| `la_aplicacion_no_depende_de_spring_security`, `nadie_usa_la_seguridad_por_metodos` (ADR 0008) | kept (pending item from #54) |
| `el_nucleo_no_depende_de_spring` | kept |
| `toda_clase_pertenece_a_una_capa` | kept, renamed `toda_clase_pertenece_al_hexagono_o_a_un_adaptador` |
| `la_persistencia_no_tiene_interfaces` (ADR 0004, phase 1) | dropped: phase 2 introduces ports (#54) |
| — | new: `los_adaptadores_de_salida_solo_implementan_puertos_del_hexagono`, `los_adaptadores_de_salida_solo_conocen_application_por_sus_puertos` (pending item from #15), `los_puertos_de_entrada_son_interfaces`, `solo_los_adaptadores_y_config_dependen_de_spring_web` |

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| tests (root) | `HexagonalArchitectureTests.java` (new) | The rules above |
| tests (root) | `LayeredArchitectureTests.java` | Deleted at the end (T4) |
| docs | `docs/arquitectura/fase-2-hexagonal.md`, `docs/adr/0006-hexagonal-architecture.md`, `docs/ia/*` | Rules section, mapping, pending items, status |

## Tasks

### [x] T1 — Onion backbone (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/HexagonalArchitectureTests.java`
- **Depends on:** —
- **What to do:**
  1. `@AnalyzeClasses(DoNotIncludeTests)` with `las_dependencias_apuntan_hacia_el_hexagono` = `onionArchitecture()` with the components above. It passes against today's code.
  2. Validate by breaking it, one at a time: a web class referencing a REST class (adapter → adapter), an output adapter referencing another one, the application referencing an adapter, the domain model referencing `domain.service`. Revert.
- **Acceptance criteria:**
  - [x] Each sabotage fails the onion rule; `./mvnw verify` green after reverting.

### [x] T2 — Ports rules (~10 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `los_adaptadores_de_salida_solo_implementan_puertos_del_hexagono`: the interfaces implemented by `adapter.out` classes are ports (interfaces of `domain.model` or `application`); a custom `ArchCondition` over `getRawInterfaces()`.
  2. `los_adaptadores_de_salida_solo_conocen_application_por_sus_puertos`: `adapter.out` depends on `application` only through interfaces (pending item from #15).
  3. `los_puertos_de_entrada_son_interfaces`: `*Commands`/`*Queries` in `application` are interfaces; carry over `los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada`.
  4. Validate each by breaking it (an adapter implementing an interface of its own package; persistence using `CheckingAccountService`; a `*Queries` class; a controller using the service). Revert.
- **Acceptance criteria:**
  - [x] Each rule fails for its sabotage and passes after reverting.

### [x] T3 — Spring, security and domain rules (~10 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests.java`
- **Depends on:** T1
- **What to do:**
  1. `solo_los_adaptadores_y_config_dependen_de_spring_web`: nothing outside `adapter..` and `config` depends on `org.springframework.web..`, `org.springframework.http..`, `org.springframework.ui..` or `org.springframework.data..` (issue rule).
  2. Carry over `el_nucleo_no_depende_de_spring`, `el_dominio_solo_depende_del_jdk` (no `org.immutables` in the domain: issue rule), `el_dominio_no_tiene_setters_publicos`, `la_aplicacion_no_depende_de_spring_security`, `nadie_usa_la_seguridad_por_metodos`.
  3. Validate each new or moved rule by breaking it (a `@RestController`-style import in `application`, `@Value.Immutable` in a domain class, a public setter, `@PreAuthorize`, a Spring Security import in `application`). Revert.
- **Acceptance criteria:**
  - [x] Each rule fails for its sabotage and passes after reverting.

### [ ] T4 — Remaining rules and goodbye to `LayeredArchitectureTests` (~10 min)

- **Layer:** tests
- **Files:** `HexagonalArchitectureTests.java`, `LayeredArchitectureTests.java` (deleted)
- **Depends on:** T2, T3
- **What to do:**
  1. Carry over `los_adaptadores_de_entrada_solo_conocen_las_excepciones_del_dominio` (D4), `solo_adapter_out_conoce_los_adaptadores_de_salida`, `nadie_depende_de_config` and `toda_clase_pertenece_al_hexagono_o_a_un_adaptador` (root of `adapter.in`, `config` and the boot class allowed). Validate the ones not yet validated by breaking them.
  2. Check the mapping table rule by rule, then `git rm LayeredArchitectureTests.java`.
- **Acceptance criteria:**
  - [ ] Every row of the mapping is covered; `LayeredArchitectureTests` is gone; `./mvnw verify` green.

### [ ] T5 — Documentation and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/fase-2-hexagonal.md`, `docs/adr/0006-hexagonal-architecture.md`, `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`
- **Depends on:** T4
- **What to do:**
  1. `fase-2-hexagonal.md`: "Rules" section points to `HexagonalArchitectureTests` (no more "until then, `LayeredArchitectureTests`"), with the onion components and the list of rules.
  2. ADR 0006: note that the provisional `LayeredArchitectureTests` was replaced in #22.
  3. Project status: #22 done, next step `/plan-feature 23`; remove the pending items from #15 and #54 about `HexagonalArchitectureTests`; decision (onion + explicit rules, mapping).
- **Acceptance criteria:**
  - [ ] No current document says `LayeredArchitectureTests` guards phase 2; the CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] `HexagonalArchitectureTests` replaces `LayeredArchitectureTests`, with `onionArchitecture()` and the issue's rules: adapters do not know each other; ports are interfaces in `domain.model` (output) or `application` (input); only `adapter..` and `config` depend on Spring web/data; `domain..` does not depend on `org.immutables`.
- [ ] Each rule validated by breaking it temporarily (listed in the PR).

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no REST change).
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a: no new business terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Where the ownership port lives:** `AccountOwnershipRepository` is an output port in `application` (ADR 0008), so the onion sees the persistence adapter depending on `application`. The onion allows adapters → application; the new rule makes sure it is only through interfaces.
- **The shared security support** (`SecuritySupport`, `CurrentCaller`, `SecurityUsersProperties` in the root of `adapter.in`) is not an adapter of its own: it stays outside the onion components, and `rest` and `web` may use it. The rule that input adapters do not know each other still holds between `cli`, `rest` and `web`.
- `@ArchTest` rules have no `@DisplayName`; their Spanish `snake_case` names are what JUnit shows, as in the existing ArchUnit classes.

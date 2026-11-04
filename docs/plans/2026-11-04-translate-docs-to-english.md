# Plan: Translate the remaining documentation to English

- **Issue:** [#70](https://github.com/dawnrock/Proyect-DDD-Claude/issues/70)
- **Type:** refactor (documentation only)
- **Phase:** 2 · Hexagonal architecture (work after the phase, no milestone)
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-04
- **Status:** in progress
- **Total estimate:** ~110 min (12 tasks)

## Goal

Apply the language convention of `CLAUDE.md` (documentation in English since #38) to the documents still written in Spanish. The meaning of every document must **not** change: this is a translation, not a rewrite. What stays in Spanish on purpose: the conversation, commits, `@DisplayName`, domain and application error messages (and every example that quotes them), and `docs/analysis/` (exception agreed in #52).

Also closes the T8 checkbox of the #23 plan (tag `v2-hexagonal`, pushed by the user and verified on 2026-11-04), as #61 did for #13.

### Out of scope

- File names: they stay as they are (`glosario.md`, `estado-del-proyecto.md`, `0001-arquitectura-ia-claude-code.md`…). Renaming would break links in `CLAUDE.md`, the skills, the agents and `scripts/check-project-status.mjs` for no gain (see open questions).
- `CLAUDE.md`, `src/test/CLAUDE.md` and `.claude/` (skills, agents, commands): their instructions are in Spanish and are not `docs/`. Only the lines that become **wrong** after the translation are touched, with the user's explicit approval (T11).
- `docs/analysis/` (stays in Spanish, #52).
- The five phase-1 plans still in Spanish: they stay as a record of finished work (user's decision, 2026-11-04).
- Code, tests, `pom.xml`: no change. Spanish strings produced by the code (error messages, `ProblemDetail` titles and details, log lines) stay as they are.

## Context

Inventory on `Hexagonal` (heuristic: Spanish vs English stop-words, then read by hand):

| Area | Files in Spanish | Words |
| ---- | ---------------- | ----- |
| ADRs | `0001`, `0002`, `0003`, `0004`, `0005`, `0007`, `docs/adr/README.md` (mixed) | ~2,200 |
| Glossary | `docs/dominio/glosario.md` | ~2,800 |
| REST contract | `docs/api/openapi.yaml`: the API description and the error table (the response examples quote real Spanish messages and stay) | ~150 |
| Contracts | `docs/contracts/README.md` (the five contracts are already in English) | ~80 |
| Diagram | `docs/arquitectura/img/hexagonal.svg`: labels ("Lado de entrada", "Aplicación", "Dominio", "Agencia tributaria (simulada)"…) | ~40 |
| `docs/ia` | `estado-del-proyecto.md`, `decisiones.md`, `pendientes.md`, `historial.md`, `notificaciones.md` | ~4,100 |
| Plans (phase 1) | `configure-lombok-immutables`, `document-glossary-layered-architecture`, `in-memory-account-repository`, `rest-web-foundation`, `team-contract-first` | ~6,000 (not in scope by default) |

Already in English: `README.md`, `docs/arquitectura/*.md`, ADRs 0006 and 0008-0010, the five contracts, `docs/ia/arquitectura-ia.md`, every other plan.

**Glossary and ubiquitous language.** Today the glossary says "the documentation, error messages and `@DisplayName` use the Spanish term". After the translation the documentation uses the English term (the one in the code), and the glossary keeps a **Spanish term** column, because error messages and `@DisplayName` still use it. Rule codes (R1-R20) do not change.

**References to Spanish headings.** `.claude/skills/implement-plan/SKILL.md` (step 6.3) tells the agent to put the issue in "Hecho", update the "Siguiente paso" and the date of "Última actualización" in `estado-del-proyecto.md`. Once those headings are in English the instruction points to text that no longer exists (T11, with approval). `CLAUDE.md` says "Los documentos que aún están en español se traducen en una issue aparte", which becomes false (T11, with approval).

## Tasks

### [x] T1 — ADRs 0001-0003 (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0001-arquitectura-ia-claude-code.md`, `0002-ramas-por-fase.md`, `0003-lombok-immutables.md`
- **Depends on:** —
- **What to do:**
  1. Translate them with the format of the English ADRs (0006, 0008): Context, Decision, Alternatives, Consequences; status "Accepted" (0003: "Accepted (the domain part, superseded by 0009)").
  2. Keep the dates, issue numbers and every link.
- **Acceptance criteria:**
  - [x] No Spanish prose left (heuristic of T12 + reading); every relative link resolves.

### [x] T2 — ADRs 0004, 0005, 0007 and the ADR index (~10 min)

- **Layer:** docs
- **Files:** `docs/adr/0004-arquitectura-en-capas.md`, `0005-thymeleaf.md`, `0007-equipo-back-web-contrato-primero.md`, `docs/adr/README.md`
- **Depends on:** —
- **What to do:**
  1. Translate the three ADRs as in T1.
  2. `README.md`: the whole index in English (titles of 0001-0005 and 0007 translated, status "Accepted").
- **Acceptance criteria:**
  - [x] The index lists the ten ADRs in English and every link resolves.

### [x] T3 — Glossary: domain terms and events (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md` (introduction and sections up to "Domain services")
- **Depends on:** —
- **What to do:**
  1. Introduction: the code and the documentation use the English term; the **Spanish term** is the one error messages and `@DisplayName` use.
  2. The term tables get the columns Term (English) · Spanish term · In code · DDD type · Definition; definitions translated, `#` references kept.
- **Acceptance criteria:**
  - [x] Every term of the current table is still there with its Spanish name; no definition changes meaning.

### [x] T4 — Glossary: business rules, persistence, application (~10 min)

- **Layer:** docs
- **Files:** `docs/dominio/glosario.md` (from "Business rules" to the end)
- **Depends on:** T3
- **What to do:**
  1. Translate the rules R1-R20 (same codes, same order, same limits and amounts), "Rules implemented without a test" and the persistence and application sections, with the same columns as T3.
  2. The `persistence` section names the phase-2 package if it still says `persistence` (`adapter.out.persistence`): it is the only factual fix allowed, because the heading would otherwise be wrong in English.
  3. Done: besides the `persistence` heading, the package names that were wrong after phase 2 are fixed in the same lines (`InMemoryAccountOwnershipRepository` in `adapter.out.persistence` behind the `AccountOwnershipRepository` port; "presentation" → "input adapters"). Amounts use English notation (€50.00); test names stay in Spanish.
- **Acceptance criteria:**
  - [x] R1-R20 all present with the same numbers and amounts (checked with a diff of the codes and numbers before and after).

### [ ] T5 — OpenAPI descriptions (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** —
- **What to do:**
  1. Translate the API description, the error table and the response descriptions. The description still says "phase 1 REST API": it becomes the API of the project (both phases share it). The `title`/`detail` values of the examples are what the API really returns and stay in Spanish; the table says so in one line.
- **Acceptance criteria:**
  - [ ] `npx @redocly/cli lint docs/api/openapi.yaml` without errors; the example payloads are byte-for-byte the same (diff).

### [ ] T6 — Contracts index and Telegram notifications (~5 min)

- **Layer:** docs
- **Files:** `docs/contracts/README.md`, `docs/ia/notificaciones.md`
- **Depends on:** —
- **What to do:**
  1. Translate both. In `notificaciones.md`, commands, variable names and paths stay as they are.
- **Acceptance criteria:**
  - [ ] Commands and paths unchanged (diff of the code blocks).

### [ ] T7 — Labels of the hexagonal diagram (~5 min)

- **Layer:** docs
- **Files:** `docs/arquitectura/img/hexagonal.svg`
- **Depends on:** —
- **What to do:**
  1. Translate the Spanish labels (input side / driving, output side / driven, Application, Domain, Command line, Persistence (in memory), Events (Spring), Tax agency (simulated), "uses"…); same layout, grayscale. **Shown to the user before committing**; check no label overflows its box at the new length.
- **Acceptance criteria:**
  - [ ] Rendered PNG approved by the user; package names unchanged.

### [ ] T8 — `decisiones.md` (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`
- **Depends on:** —
- **What to do:**
  1. Translate it entry by entry ("Decisions already made that affect what comes next", "Environment"), keeping issue numbers, dates and the "decided by the user on …" mentions.
- **Acceptance criteria:**
  - [ ] Same number of entries before and after; every `#n` and date kept.

### [ ] T9 — `pendientes.md` and `historial.md` (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/pendientes.md`, `docs/ia/historial.md`
- **Depends on:** —
- **What to do:**
  1. Translate both. In `pendientes.md`, remove the two items this issue resolves (translation of `docs/`, and the #5 additions and annexes "translated with the rest"); the history table keeps one row per PR with the same columns.
- **Acceptance criteria:**
  - [ ] `historial.md` has the same number of rows; `pendientes.md` keeps every open item except the two resolved ones.

### [ ] T10 — `estado-del-proyecto.md` (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`
- **Depends on:** T8, T9
- **What to do:**
  1. Translate it, keeping it short; headings "Where we are", "Next step", "Working agreements with the user", "Annexes". The agreement on language says: conversation, commits, `@DisplayName` and domain messages in Spanish; documentation in English (all of it since #70, except `docs/analysis/`).
  2. Annex table links unchanged (file names stay).
- **Acceptance criteria:**
  - [ ] Every agreement still there with the same meaning; links resolve.

### [ ] T11 — Lines that would become wrong in `CLAUDE.md` and `implement-plan` (~5 min, needs the user's approval)

- **Layer:** docs (`CLAUDE.md`, `.claude/`)
- **Files:** `CLAUDE.md`, `.claude/skills/implement-plan/SKILL.md`
- **Depends on:** T10
- **What to do:**
  1. Approved by the user on 2026-11-04 (agreement on `CLAUDE.md` and `.claude/`): `CLAUDE.md` loses "Los documentos que aún están en español se traducen en una issue aparte" (it says instead that `docs/analysis/` is the only exception); step 6.3 of `implement-plan` names the English headings ("Done", "Next step", "Last updated"). Both stay in Spanish; nothing else changes.
- **Acceptance criteria:**
  - [ ] Both lines changed; nothing else in `CLAUDE.md` or `.claude/` changes (diff).

### [ ] T12 — Check and project status (~10 min)

- **Layer:** docs
- **Files:** `docs/ia/estado-del-proyecto.md`, `decisiones.md`, `historial.md`, `docs/plans/2026-11-04-close-phase-2.md`
- **Depends on:** T1-T11
- **What to do:**
  1. Run the stop-word heuristic over `docs/` (outside `docs/analysis/` and the phase-1 plans): no file with more Spanish than English; read the hits by hand (quoted messages are fine). Check every relative link of `docs/` resolves.
  2. Status: #70 done, next step `/plan-feature 71`; decision: documentation fully in English except `docs/analysis/` and the five phase-1 plans; file names stay in Spanish; a row in `historial.md`. Mark T8 of the #23 plan as done.
- **Acceptance criteria:**
  - [ ] The heuristic and the link check pass; the CI check of the project status passes.

## Global acceptance criteria

**Specific:**

- [ ] No document in `docs/` outside `docs/analysis/` and the five phase-1 plans is still in Spanish.
- [ ] Every relative link resolves.
- [ ] Quoted Spanish strings produced by the code (OpenAPI examples, messages in the glossary) are unchanged.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit. Unchanged: no code changes.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md`: n/a (no new tests).
- [ ] No dependencies added beyond the planned ones.
- [ ] If the API changes: n/a (only descriptions change); `npx @redocly/cli lint docs/api/openapi.yaml` has no errors.
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no new terms).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

Answered by the user on 2026-11-04: (1) the phase-1 plans stay in Spanish as a record; (2) file names stay in Spanish; (3) T11 approved (only the two lines named there).

- **Phase-1 plans in Spanish (5 files, ~6,000 words).** The issue assumed every plan was in English; five of the first ones are not. They are a record of finished work. Proposal: leave them as they are and record the exception (cost ~40 min more and no reader needs them in English). If you want them translated, they become two more tasks.
- **File names in Spanish** (`glosario.md`, `estado-del-proyecto.md`, `decisiones.md`, ADR slugs…): kept, because renaming touches `CLAUDE.md`, skills, agents and the CI script. Could be a separate issue if you want English names too.
- **`CLAUDE.md` and `implement-plan` (T11)** need your explicit approval: only the two lines that would become wrong, nothing else.
- **`estado-del-proyecto.md` in English** is read at the start of every session; the agents read English without trouble, and the conversation stays in Spanish.
- **Size of the PR:** ~10,000 words of translation in one PR; each task is one commit, so it can be reviewed task by task.

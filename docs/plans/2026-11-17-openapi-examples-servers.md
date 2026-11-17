# Plan: Named response examples, the 415 error and the Render server in openapi.yaml

- **Issue:** [#110](https://github.com/dawnrock/Proyect-DDD-Claude/issues/110)
- **Type:** feature
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-17
- **Status:** in progress
- **Total estimate:** ~50 min (6 tasks)

## Goal

Complete `docs/api/openapi.yaml` so it works as documentation and as the base of a mock server (OpenAPI course, 9.9): examples on every success response, named examples for the interesting cases, the Render server, and the one error the API returns but the contract does not document (415, which also answers in English). `info.contact` already came with #109.

### Out of scope

- Swagger UI (#111), versioning (#112).
- Request examples beyond the existing schema `examples`.

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| `adapter.in.rest` | `RestExceptionHandler.java` | 415 with the Spanish `title` and `detail`, like every other error |
| docs | `docs/api/openapi.yaml` | `UnsupportedMediaType` response on the 5 operations with a body; Render server; response examples |
| docs | `docs/deploy/render.md` | The real URLs of both deployments |

Data from the user (2026-11-17): `Hexagonal` is deployed at `https://proyect-ddd-claude.onrender.com` (goes to `servers`), `Monolithic` at `https://mon-e-bank-monolithic.onrender.com` (only in the deploy guide: `Monolithic` is frozen).

Decisions taken while planning (clear and reversible, so not asked):

- **415 in Spanish:** `title` "Tipo de contenido no admitido", `detail` naming the received type and `application/json`, through `RestExceptionHandler` like the other errors (the convention of #108 and the style guide). Documented as a shared `UnsupportedMediaType` response.
- **Examples are real responses** captured from the app with the `demo` profile (Mon-A Bank IBANs, demo customers, Spanish error messages), so a mock or Swagger UI shows what the API really returns. Redocly's `no-invalid-media-type-examples` (in `recommended`) checks each one against its schema.

Named examples:

| Operation | Examples |
| --------- | -------- |
| `listAccounts` | `several` (the three demo accounts), `empty` |
| `openAccount`, `signUp` | `retail`, `highNetWorth` (`openAccount`); `newCustomer` (`signUp`) |
| `findAccount` | `withTransfer` (a deposit and a transfer withdrawal with `counterpartAccountNumber`) |
| `regenerateAccountNumber` | `newIban` |
| `deposit` | `approved` |
| `withdraw` | `approved`, `rejected` (overdraft not allowed, still 201) |
| `transfer` | `approved`, `rejected` |
| `UnprocessableContent` (shared) | `minimumInitialDeposit`, `currencyMismatch`, `sameAccount`, `duplicateDocument` |

## Tasks

### [x] T1 — 415 in Spanish (~10 min)

- **Layer:** adapter.in
- **Files:** `RestExceptionHandler.java`, `RestExceptionHandlerTests.java` (or `AccountControllerTests.java`)
- **Depends on:** —
- **What to do:**
  1. Test first: `POST /api/accounts/{id}/deposits` with `Content-Type: text/plain` answers 415 `application/problem+json` with `title` "Tipo de contenido no admitido" and a Spanish `detail` (today: "Unsupported Media Type").
  2. Override `handleHttpMediaTypeNotSupported` (keep the `Accept` header Spring adds).
- **Acceptance criteria:**
  - [x] Red before, green after; `./mvnw verify` green.

### [x] T2 — Document 415 (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T1
- **What to do:**
  1. `components.responses.UnsupportedMediaType` (ProblemDetail, real example) referenced from `openAccount`, `signUp`, `deposit`, `withdraw` and `transfer`; one row in the error table of `info.description`.
- **Acceptance criteria:**
  - [x] Redocly clean; `node scripts/contract-test.mjs` passes.

### [ ] T3 — Render server and deploy guide (~5 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`, `docs/deploy/render.md`
- **Depends on:** —
- **What to do:**
  1. `servers`: add `https://proyect-ddd-claude.onrender.com` ("Render deployment of `Hexagonal`; free plan, the first request may take a minute while it wakes up"). `name` is OpenAPI 3.2, so only `description`.
  2. `render.md`: the two real URLs.
- **Acceptance criteria:**
  - [ ] Redocly clean (the `no-server-example.com` rule stays off only for `localhost`).

### [ ] T4 — Account examples (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T2
- **What to do:**
  1. Named examples of `listAccounts`, `openAccount`, `findAccount`, `regenerateAccountNumber` and `signUp` from the table.
- **Acceptance criteria:**
  - [ ] Redocly clean (examples valid against their schemas).

### [ ] T5 — Movement, transfer and business-rule examples (~10 min)

- **Layer:** docs
- **Files:** `docs/api/openapi.yaml`
- **Depends on:** T4
- **What to do:**
  1. Named examples of `deposit`, `withdraw`, `transfer` and the shared `UnprocessableContent` (its single `example` becomes `examples`).
  2. Redocly and the contract tests.
- **Acceptance criteria:**
  - [ ] Redocly clean; contract tests pass.

### [ ] T6 — Project status (~5 min)

- **Layer:** docs
- **Files:** `docs/ia/decisiones.md`, `docs/ia/pendientes.md`
- **Depends on:** T5
- **What to do:**
  1. `decisiones.md`: 415 in Spanish; examples are real responses. `pendientes.md`: remove the 415 note.
- **Acceptance criteria:**
  - [ ] Annexes updated.

## Global acceptance criteria

**Specific:**

- [ ] Every success response has at least one example, and the interesting cases have named ones.
- [ ] The API answers every documented error in Spanish, 415 included.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: `docs/api/openapi.yaml` reflects it, `npx @redocly/cli lint docs/api/openapi.yaml` has no errors and `node scripts/contract-test.mjs` passes.
- [ ] Any new business term is in `docs/dominio/glosario.md` (n/a).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- **Generated values in examples** (ids, IBANs, dates) are real but will differ from a fresh run; they illustrate the shape, not fixed data.
- **Render free plan** sleeps when idle: "Try it out" against it (#111) may take a minute on the first request.

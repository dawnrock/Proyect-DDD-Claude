# 0013 · Contract tests with Schemathesis, at hexagon level

- **Status:** accepted · 2026-11-17
- **Issue:** #107 · **Related:** [ADR 0007](0007-equipo-back-web-contrato-primero.md) (contract first)

## Context

The REST contract is designed first (`docs/api/openapi.yaml`, ADR 0007) and Redocly checks in the CI that it is a valid document, but nothing checked that the implementation keeps to it. The OpenAPI course (chapter 3.6, "synchronisation between contract and implementation", and the mock-server lab, exercise 8) names this as the main risk of API First.

Running Schemathesis by hand showed the drift was real: #108 (amounts the contract accepted and the API rejected, an undocumented 401, `QUERY` answering 400, `null` accepted where the contract said `string`) and #113 (a 500 for `multipart/form-data` without a boundary).

In this architecture the OpenAPI document is the contract of the driving REST adapter (`adapter.in.rest`), not of the hexagon: the hexagon exposes input ports, the adapter translates HTTP to them. A contract test can therefore sit at two levels:

- **Adapter in isolation:** the existing `@WebMvcTest`s, with the ports mocked, also validating each request and response against the YAML. It catches translation errors (DTOs, status codes, headers, `ProblemDetail`), but nothing that depends on the domain, because the mocked ports return whatever the test invents.
- **Whole hexagon, as a black box:** the real application over HTTP, with security, application, domain and output adapters. It also catches rules of the domain that the contract does not state.

Every drift found so far came from the domain (rounding of amounts, no maximum) or from the framework around the adapter (firewall, multipart resolution): an adapter-level test would have missed all of them.

## Decision

- **Contract tests at the whole-hexagon level**, with [Schemathesis](https://schemathesis.readthedocs.io/) 4.33.4: it generates valid and invalid requests from the contract and checks every response against it.
- `scripts/contract-test.mjs` starts the built jar with the `demo` profile, waits for `/actuator/health`, runs Schemathesis and stops the application. The CI runs it after `./mvnw verify` on every PR; locally it is `node scripts/contract-test.mjs` (`CLAUDE.md`), and `implement-plan` runs it when a plan touches the API.
- **Schemathesis is a CI tool, like Redocly**: installed with `pip` at a fixed version, not a Maven dependency. The application and its tests do not know it.
- The configuration lives in `schemathesis.toml`, versioned with the contract:
  - **Deterministic** (`seed` + `generation.deterministic`): the CI does not turn red because a new random case appeared. Exploring new cases is a deliberate change of the seed, and what it finds is handled in its own issue.
  - **Legitimate rejections are declared, never disabled**, and only on the operations that document them. The check that valid data is accepted (`positive_data_acceptance`) keeps running with Schemathesis' default statuses everywhere; `deposit`, `withdraw`, `transfer`, `openAccount` and `signUp` also accept `422` (business rules the schema cannot express), and `openAccount` and `signUp` also accept `400` for rules a pattern cannot check and the contract documents in text (the DNI/NIE control letter; the #103 rule for an existing customer's document). Every other check keeps its defaults.
  - **Accepted blind spot:** a 400 cannot say which rule caused it, so on `openAccount` and `signUp` a validation 400 that disagrees with the contract (the kind of drift #108 fixed for `initialDeposit`) is not caught by this check. The REST adapter tests cover those fields (#108); valid documents as examples in the contract would let the 400 be removed from `signUp`.

## Alternatives

- **Adapter-level validation** (`swagger-request-validator-mockmvc` in the `@WebMvcTest`s): faster and inside `./mvnw verify`, but blind to the domain, and a new test dependency whose OpenAPI 3.1 support must be checked. Kept as an optional later step, with its own ADR if it is ever added.
- **Prism as a validating proxy** (the course's exercise 6): it only checks the traffic someone sends, so it needs hand-written tests to drive it; Schemathesis generates the traffic itself.
- **Schemathesis from its Docker image**: no Python in the runner, but slower to start and awkward to reach the application on the host; `pip` at a fixed version is enough.
- **Hand-written REST tests only**: they check the cases someone thought of; the drifts of #108 and #113 were all cases nobody had written.

## Consequences

- A PR whose implementation drifts from `openapi.yaml` fails in the CI, as the T2 sabotage of #107 showed (`exclusiveMinimum: 0` back on an amount → `ST202`).
- About one more minute per CI run (start the jar, ~870 generated cases), and Python in the runner.
- The contract cannot express business rules, so their statuses must be declared in `schemathesis.toml`; adding a new kind of legitimate rejection is a reviewed change of that file.
- Schemathesis still warns that `POST /api/transfers` mostly gets 404 (it invents account ids) and that `signUp` rejects most generated documents (a random DNI rarely has a valid letter). They are warnings, not failures; better test data would let it reach more of the logic.

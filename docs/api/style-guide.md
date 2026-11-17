# OpenAPI style guide

Conventions for `docs/api/openapi.yaml` (#109). Most of them are rules in [`redocly.yaml`](../../redocly.yaml), checked by the CI's Redocly step on every PR (`npx @redocly/cli@2.71.0 lint docs/api/openapi.yaml`); the rest are checked by people when reviewing the contract (the OpenAPI course, chapter 7.4: a valid document is not necessarily a well-designed API). The contract tests (ADR 0013) then check that the implementation keeps to it.

## Checked by Redocly

Rules named `rule/…` are the project's own assertions; the others are Redocly built-in rules raised to `error`.

| Convention | Rule |
| ---------- | ---- |
| Every operation has a `summary` | `operation-summary` (error) |
| Every operation has exactly one tag, declared in the root `tags` | `rule/one-tag`, `operation-tag-defined` (error) |
| `operationId` in camelCase (`openAccount`, `listAccounts`) | `rule/operation-id-camel-case` |
| Every operation documents a success (2XX) and at least one error (4XX) | `operation-2xx-response`, `operation-4xx-response` (error) |
| Errors (4XX) with a body are `ProblemDetail` (RFC 9457) as `application/problem+json`, never `application/json` | `rule/errors-are-problem-details` |
| A request body always declares `required: true` (OpenAPI defaults it to `false`) | `rule/request-body-required`, `rule/request-body-required-true` |
| `info.contact` says who is responsible for the API | `rule/contact-required` |
| Inside schemas, examples go in `examples` (a list); `example` is deprecated there since OpenAPI 3.1 | `rule/no-schema-example` |
| Every media type example (request and response bodies) is valid against its schema (#110) | `no-invalid-media-type-examples` (error) |
| Plus Redocly's `recommended` set (valid structure, resolvable `$ref`, path parameters declared, unique `operationId`, license…) | `extends: recommended` |

## Checked by people

- **Resources, not actions:** plural nouns (`/api/accounts`, `/api/transfers`, `/api/customers`); sub-resources for what belongs to one (`/api/accounts/{id}/deposits`). An action that has no natural resource is a `POST` on a sub-resource named after its result (`/api/accounts/{id}/account-number`).
- **Status codes:** `201` with a `Location` header when a resource is created; `400` for an invalid request (format, validation, an argument the domain rejects); `401` without valid credentials; `403` without the role; `404` for a missing resource or another customer's account (its existence is not revealed); `422` for a business rule.
- **Errors in Spanish:** the `title` and `detail` values are the real ones the API returns, in Spanish, and they are not translated in the contract or its examples (`Petición no válida`, `Regla de negocio incumplida`…).
- **Reuse:** responses, parameters and schemas used more than once live in `components` and are referenced with `$ref`.
- **Precise schemas:** formats (`uuid`, `date-time`), ranges, patterns and `enum` whenever the API enforces them, so the contract tests and any mock can rely on them; a rule a schema cannot express (a control letter, a business rule) is described in the `description` and its status is declared in `schemathesis.toml`.
- **Versioning:** every change bumps `info.version` following [ADR 0014](../adr/0014-contract-versioning.md) (major if `oasdiff breaking` reports it, minor for an addition, patch for documentation) and adds an entry to [`CHANGELOG.md`](CHANGELOG.md); the CI checks it. Descriptions do not carry "incompatible change" notes.
- **Language:** the contract is documentation, so its texts are in English, except the API's own error messages.

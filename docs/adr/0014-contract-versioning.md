# 0014 · Semantic versioning of the OpenAPI contract

- **Status:** accepted · 2026-11-17
- **Issue:** #112 · **Related:** [ADR 0007](0007-equipo-back-web-contrato-primero.md) (contract first), [ADR 0013](0013-contract-tests-schemathesis.md) (contract tests)

## Context

`info.version` of `docs/api/openapi.yaml` stayed at `0.1.0` through several incompatible changes (#18 changed the body of `POST /api/accounts`, #103 the holder's document, #108 restricted the amounts and the currency). They were only announced in the operations' descriptions, so a consumer could not tell from the version whether the contract had broken.

The OpenAPI course (1.6, 3.4 phase 7, 7.3 and 8.9) asks to treat the contract as a product: every change is a new version, incompatible changes are announced, and a tool compares two versions to catch them before they reach the consumers.

## Decision

- `info.version` follows **Semantic Versioning**, decided by what changed in the contract:
  - **Major:** an incompatible change, as [`oasdiff`](https://github.com/oasdiff/oasdiff) `breaking` reports it at `ERR` level (an operation or field removed, a field made required, accepted values restricted, a status code or type changed…). The tool decides, so the rule is mechanical, even when the change only documents what the API already rejected.
  - **Minor:** a compatible addition (a new operation, optional field or documented response).
  - **Patch:** documentation only (descriptions, examples, servers, tags).
- **Every change to the contract bumps the version**, so a version always names one exact document.
- **Baseline `1.0.0`** (#112): after the OpenAPI course review (#107-#113) the contract is complete and checked against the implementation. Earlier versions are not renumbered; their incompatible changes are recorded in `docs/api/CHANGELOG.md`.
- **Changelog:** `docs/api/CHANGELOG.md` gets an entry per version; operation descriptions no longer carry "Incompatible change" notes.
- **Enforced in the CI:** on every pull request, `scripts/check-contract-version.mjs` compares the PR's contract with the base branch's: if it changed, `info.version` must be higher, and if `oasdiff breaking --fail-on ERR` finds incompatible changes, the major number must be higher too. oasdiff `v1.35.0` is installed with `go install`, a CI tool like Redocly and Schemathesis, not a Maven dependency.
- `plan-feature` asks for the bump and the changelog entry in the API task of every plan that touches the contract.

## Alternatives

- **No version check:** the version would drift again, as it did up to `0.1.0`.
- **Version in the URL (`/api/v1/...`):** useful to serve two incompatible versions at once; this project has one API and one consumer set, so it would only add a path segment. It can come later, if two versions ever need to live together.
- **Another diff tool** (Redocly has no breaking-change diff in its CLI; `openapi-diff` in Java would be a Maven dependency): oasdiff supports OpenAPI 3.1, runs as a single binary and is widely used.

## Consequences

- A PR that changes the contract without the right bump fails, and its message says which rule.
- The version tells consumers what to expect: a new major means their client may need changes.
- About one more minute of CI on pull requests (`go install` compiles oasdiff).
- oasdiff's notion of "breaking" may be stricter than the project's; the policy follows the tool, and a doubtful case is discussed in the PR rather than worked around.

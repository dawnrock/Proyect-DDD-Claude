# Plan: PostgreSQL (Neon) connection timeouts in the prod profile

- **Issue:** [#156](https://github.com/dawnrock/Proyect-DDD-Claude/issues/156)
- **Type:** fix
- **Phase:** 2 · Hexagonal architecture
- **Base branch:** `Hexagonal`
- **Date:** 2026-11-24
- **Status:** in progress
- **Total estimate:** ~25 min (3 tasks)

## Goal

On 2026-11-23 the first deploy against Neon hung with no error until Render gave up after 15 minutes; it only started once the timeouts were added to `SPRING_DATASOURCE_URL` (`connectTimeout=15&loginTimeout=30&socketTimeout=120`). By default the PostgreSQL driver waits forever while logging in (`loginTimeout=0`) and reading (`socketTimeout=0`), so a URL copied from Neon without those parameters can bring the hang back.

- **Current:** the timeouts only exist in a Render variable; with the plain URL a silent server leaves the start hanging.
- **Expected:** the `prod` profile sets the driver timeouts itself, so the URL is back to `…?sslmode=require` and a failed connection at start ends in an error with a stack trace within the configured times.

### Out of scope

- Testcontainers or any check against a real PostgreSQL (new dependency, needs Docker in the CI).
- Changing the pool size or the H2 configuration of the default profile and the tests.
- The event outbox and OAuth (next issues).

## Context

| Layer / package | File | Change |
| --------------- | ---- | ------ |
| config (resources) | `src/main/resources/application-prod.properties` | Driver timeouts as `spring.datasource.hikari.data-source-properties.*`, plus explicit Hikari `connection-timeout`, `initialization-fail-timeout` and `keepalive-time` |
| tests | `src/test/java/com/pronoide/ddd/ProdProfileTests.java` | The profile exposes the timeouts; a silent server ends the start in an error, not a hang |
| docs | `docs/deploy/render.md` (section 2 bis), `docs/adr/0019-*.md`, `docs/ia/*` | Plain URL, why the timeouts exist, which log appears when they run out, how it was diagnosed |

Technical decisions (taken by Claude under `/advance-issues`, reversible configuration):

- **Driver timeouts (seconds):** `connectTimeout=15` (TCP + SSL handshake), `loginTimeout=30` (whole connection attempt, Neon's cold start took ~7 s), `socketTimeout=120` (any read; the longest expected query is milliseconds, Flyway's migrations included). They are the values checked on Render on 2026-11-23. They go in `data-source-properties`, which HikariCP passes to the driver, so they apply whatever the URL says (a parameter in the URL wins over them, as pgjdbc gives the URL priority).
- **`connection-timeout=30000` (ms):** how long a request waits for a pool connection (Hikari's default, made explicit).
- **`initialization-fail-timeout=1`:** one connection attempt at start, bounded by `loginTimeout`; if it fails the start fails with the exception (Hikari's default, made explicit). Render keeps the previous version when a deploy fails, so retrying inside the JVM adds nothing.
- **`keepalive-time=120000` (ms):** Hikari's default, made explicit and below Neon's 5-minute suspension, so the idle connection does not go stale while the Render instance is awake. It does not keep Neon awake for longer than Render: when Render sleeps (15 minutes idle) the JVM stops, the connections close and Neon suspends five minutes later.
- The diagnosis is recorded in ADR 0019 (consequences) and the values in `decisiones.md`.

## Tasks

### [x] T1 — The prod profile sets the driver and pool timeouts (~10 min)

- **Layer:** config
- **Files:** `src/main/resources/application-prod.properties`, `src/test/java/com/pronoide/ddd/ProdProfileTests.java`
- **Depends on:** —
- **What to do:**
  1. Red: a test that starts the `prod` profile with the H2 URL and checks the `HikariDataSource`: `dataSourceProperties` with `connectTimeout=15`, `loginTimeout=30`, `socketTimeout=120`, and `connectionTimeout`, `initializationFailTimeout` and `keepaliveTime` as above.
  2. Green: add the properties, with a comment explaining each one and the hang of 2026-11-23.
- **Acceptance criteria:**
  - [x] The new test fails without the properties and passes with them; the H2 driver accepts the extra properties.
  - [x] `./mvnw verify` green.

### [x] T2 — A silent database ends the start in an error, not a hang (~10 min)

- **Layer:** tests
- **Files:** `src/test/java/com/pronoide/ddd/ProdProfileTests.java`
- **Depends on:** T1
- **What to do:**
  1. A test opens a local `ServerSocket` that accepts connections and never answers (as a compute that does not wake up), starts `prod` against `jdbc:postgresql://127.0.0.1:<port>/monabank` with `loginTimeout` lowered to 2 s (the only override, to keep the test fast), and checks with `assertTimeoutPreemptively` that the start fails within a few seconds with the driver's timeout in the stack trace.
  2. Show it can fail: with `loginTimeout=0` the test must hit its own timeout (red), then revert.
- **Acceptance criteria:**
  - [x] The test passes in a few seconds; with `loginTimeout=0` it fails on `assertTimeoutPreemptively` (checked: `execution timed out after 20000 ms`, the driver waiting on `Read timed out`).
  - [x] `./mvnw verify` green.

### [ ] T3 — Document the plain URL and the timeouts (~5 min)

- **Layer:** docs
- **Files:** `docs/deploy/render.md`, `docs/adr/0019-*.md`
- **Depends on:** T1
- **What to do:**
  1. `render.md` section 2 bis: the URL is just `…?sslmode=require`; a paragraph on why there are timeouts and which log appears when they run out (`Connection attempt timed out` / `SocketTimeoutException` and the start stops), and how to override them.
  2. ADR 0019, consequences: the hang of 2026-11-23, how it was diagnosed (Render's API logs and Neon's read-only tools) and the fix.
- **Acceptance criteria:**
  - [ ] The docs say the same values as `application-prod.properties`.

## Global acceptance criteria

**Specific:**

- [ ] With the URL without parameters, a failed connection at start with `prod` ends in an error with a stack trace within the configured times, not a hang (T2).
- [ ] `ProdProfileTests` checks the timeouts (T1).
- [ ] Render deploys with the plain URL and starts against Neon: **checked by the user after the merge** (changing the Render variable is theirs; noted in `pendientes.md` until confirmed).
- [ ] `render.md`, `pendientes.md` and `estado-del-proyecto.md` updated.

**Fixed:**

- [ ] All tasks and their criteria are checked.
- [ ] `./mvnw verify` ends in `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` starts and `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] The phase architecture is respected (hexagonal: dependencies point to the hexagon), verified by ArchUnit.
- [ ] The domain only depends on the JDK (rule D1) and does not use Lombok (ADR 0009) (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` without errors).
- [ ] New tests follow `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` in Spanish).
- [ ] No dependencies added beyond the planned ones (or justified in an ADR).
- [ ] If the API changes: n/a (no API change).
- [ ] Any new business term is in `docs/dominio/glosario.md`: n/a (no business term).
- [ ] Documentation written or changed by this plan is in English.
- [ ] Project status updated: issue status and next step in `docs/ia/estado-del-proyecto.md` (kept short), new decisions in `decisiones.md`, pending items in `pendientes.md` and a row in `historial.md`.

## Risks and open questions

- The root cause is probable, not confirmed (the first attempt waited forever while Neon woke up). The timeouts turn any such wait into a visible error; if it happens again on Render, the log now says where.
- The real deploy with the plain URL can only be checked on Render after the merge (the user changes the variable, or Claude reads the deploy logs with Render's API).

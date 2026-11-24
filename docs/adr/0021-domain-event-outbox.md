# 0021 · Domain event outbox with a scheduled relay

- **Status:** accepted · 2026-11-24 (options 1A, 2A, 3A2 and 4A chosen by the user in #165)
- **Issue:** #165 · **Related:** [ADR 0006](0006-hexagonal-architecture.md) (output ports), [ADR 0019](0019-real-persistence.md) (real persistence), [ADR 0020](0020-static-analysis.md) (static analysis)

## Context

Since #17 the aggregates record domain events (`CheckingAccountOpened`, `MoneyDeposited`, `MoneyWithdrawn`, `WithdrawalRejected`, `CustomerSegmentChanged`) and hand them over with `pullDomainEvents()`. `CheckingAccountService.inUnit` published them through the `DomainEventPublisher` port **after** the unit of work had committed, and `SpringDomainEventPublisher` delivered them synchronously to the `@EventListener`s of `application.listener`. That had two risks, noted in `pendientes.md` since #17:

- An event was lost if the process stopped between the commit and the publication: the account was saved and nobody heard about the movement.
- A consumer that threw broke the use case after the commit: the error reached the user with the money already moved, and the events still to publish were lost.

Harmless while the consumers only write to the log, but the first real consumer (e-mail, Telegram) turns them into bugs. Since ADR 0019 there is a database in which to keep the events in the same transaction as the aggregate.

## Decision

- **Transactional outbox.** The application publishes the events **inside** the unit of work, after the last save. `OutboxDomainEventPublisher` (`adapter.out.event`) implements `DomainEventPublisher` by inserting one row per event in `domain_event_outbox` (Flyway `V2`), in that same transaction: if the use case rolls back, its events disappear; if it commits, they survive a crash. Outside a transaction the publisher throws `IllegalTransactionStateException`.
- **Scheduled relay only (1A).** `OutboxRelay`, in the same adapter (2A), reads the pending rows in `id` order every `app.events.outbox.relay-interval` (2 s, `fixedDelay`, never overlapping itself; the first run also waits one interval) and delivers each event to the `@EventListener`s through Spring's `ApplicationEventPublisher`, in its own transaction. A failure counts an attempt and the event is retried on the next run; after `max-attempts` (5) it is marked failed with a `log.error` and left for inspection. Published rows older than `retention` (7 days) are deleted daily at 03:30.
- **Delivery at least once.** If the process stops after delivering and before marking, the event is delivered again. Consumers must tolerate a duplicate.
- **Payload: Jackson with a logical name (3A2).** The events are `record`s, so Jackson 3 writes and reads them through their canonical constructor with no annotations in the domain; derived `is…()` accessors are not written. The type column stores the simple name (`MoneyDeposited`), resolved by an explicit registry, `DomainEventTypes`, so a row is still readable if the class moves; a test fails if a `DomainEvent` is not registered.
- **One instance (4A).** The relay reads pending rows without row locks, like the global lock of the unit of work (#78). With several instances, `SELECT … FOR UPDATE SKIP LOCKED` works on PostgreSQL and on H2 2.4 in PostgreSQL mode.
- **Only the adapter talks to Spring's event bus**, checked by `HexagonalArchitectureTests.onlyTheEventAdapterPublishesToSpring`. The domain does not change.

## Alternatives

- **`@TransactionalEventListener(AFTER_COMMIT)`:** removes the "consumer breaks the use case" risk but not the lost event after a crash, and it ties consumers to Spring's transaction phases.
- **Publish right after the commit (in the same thread or `@Async`) with the scheduled relay as a safety net (1B, 1C):** less delay, but two delivery paths to reason about. Kept as the option if a consumer ever needs the event at once; the table would not change.
- **`LISTEN/NOTIFY` or change data capture:** PostgreSQL-only or more infrastructure; not justified for local consumers.
- **Relay as an input adapter with new ports (2B), or the pieces split between `adapter.out.persistence` and `adapter.out.event` (2C):** more ports and packages for a mechanism the core does not need to know about.
- **Class name as type (3A1), a DTO per event (3B) or Java serialisation (3C):** fragile against renames, more code, or opaque and unsafe.

## Consequences

- Consumers receive events up to ~2 s later than before; the audit log lines appear on the `scheduling-1` thread.
- Duplicates are possible; today's consumers only write to the log, and future ones must be idempotent.
- While Render is awake the relay queries Neon every 2 s; Neon already stays awake then (health check and keepalive, #156), so no extra compute.
- Full-context tests that need the relay to stay idle set `app.events.outbox.relay-interval=1h` and call `OutboxRelay.relayPending()` themselves (`OutboxDeliveryTests`, `OutboxRestartTests`).

# Project history

> Annex of [`estado-del-proyecto.md`](estado-del-proyecto.md). **It is not read at the start of every session**: every PR that closes an issue adds its row here.

| Date | PR | Issue | Change |
| ---- | -- | ----- | ------ |
| 2026-10-23 | #1 | — | AI architecture on `Monolithic` |
| 2026-10-23 | #24 | #2 | Lombok and Immutables in the build |
| 2026-10-23 | #32 | #31 (partial) | Back/web team flow and reorganised backlog |
| 2026-10-23 | #33, #34 | #3 | Glossary, layers, ADR 0004 and hexagonal diagram |
| 2026-10-23 | #35 | #4 | In-memory repository |
| 2026-10-23 | #36 | #8 | REST + web foundation: errors to HTTP, OpenAPI, Thymeleaf and 404 page |
| 2026-10-23 | #37 | — | Project status between sessions, required in every PR |
| 2026-10-23 | #38 | — | Documentation in English; #5 plan approved |
| 2026-10-23 | #39 | #5 | Open, query and list accounts (REST + web), first team issue; also closes #31 (team flow) |
| 2026-10-23 | #40 | — | `TELEGRAM_DISABLED` switch to silence the notices |
| 2026-10-24 | #41 | #6 | Deposits and withdrawals with account details (REST + web); closes the pending item on inherited synonyms |
| 2026-10-25 | #43 | #7 | Transfers between accounts (REST + web): first domain service `MoneyTransferService` (R17-R19) |
| 2026-10-25 | #44 | — | Removes the deny rule `Write(./.claude/hooks/**)`, which never applied |
| 2026-10-25 | #45 | #11 | Demo data with the `demo` profile and the `TestAccounts` test account builder |
| 2026-10-25 | #46 | — | Removes from the status the resolved pending item on the teammates' branches |
| 2026-10-25 | #47 | #12 | `LayeredArchitectureTests`: D1-D5 of phase 1, no public setters in the domain and controllers without the repository |
| 2026-10-25 | #49 | #48 | Dark minimal web redesign, `WebFormat` and amounts in Spanish format |
| 2026-10-25 | #51 | #50 | Login with admin and user roles (Spring Security, ADR 0008), account ownership and transfer by IBAN |
| 2026-10-26 | #52 | — | Functional and technical analysis (`docs/analysis/`, in Spanish as an exception) and `check-domain` for `@EqualsAndHashCode` |
| 2026-10-26 | #53, #55 | — | ADR 0009: the domain does not use Lombok; `check-domain` rejects any `lombok`; ArchUnit references fixed |
| 2026-10-26 | #56 | #54 | ArchUnit rules of ADRs 0004 and 0008 and `DomainIsolationTests` without the `bootstrap` package |
| 2026-10-30 | #58 | #57 | Graphify only for dependency questions (ADR 0010) and project status split into a short status + annexes |
| 2026-10-30 | #60 | #13 | Phase 1 closed: `README`, `fase-1-capas.md` and `arquitectura-ia.md` in English with the final state, tag `v1-monolito` and `Hexagonal` as the default branch |
| 2026-10-30 | #61 | #14 | Hexagonal packages: `presentation` → `adapter.in`, `persistence` → `adapter.out.persistence` (`git mv`), ADR 0006 and class → role table; T7-T8 of #13 checked |
| 2026-10-30 | #62 | #15 | Output ports `CheckingAccountRepository` (`domain.model`) and `AccountOwnershipRepository` (`application`) with test contracts; package-private in-memory adapters and ArchUnit rule; Telegram back on in the project status |
| 2026-11-03 | #63 | #16 | Input ports `CheckingAccountCommands` and `CheckingAccountQueries`; REST/web controllers and `DemoDataLoader` only know them (`@MockitoBean` of the ports and ArchUnit rule); no query objects and no renaming of the service |
| 2026-11-03 | #64 | #17 | Domain events (`CheckingAccountOpened`, `MoneyDeposited`, `MoneyWithdrawn`, `WithdrawalRejected`) published after saving through the `DomainEventPublisher` port and its `SpringDomainEventPublisher` adapter; audit and customer-notification consumers (simulated); ArchUnit over `adapter.out..` |
| 2026-11-03 | #65 | #18 | Account factory with the `AccountNumberSequence` (generated IBAN) and `IncomeVerifier` (simulated tax agency) ports and rule R20; opening by NIF on REST and web; first team issue of phase 2 (contract agreed in one round) |
| 2026-11-03 | #66 | #19 | `DemoDataLoader` becomes the CLI adapter `adapter.in.cli.DomainModelRunner`, with a `[demo]` trace and, at the user's request, deposits, withdrawals and a transfer; agreement to delete each PR's branches and worktrees once merged |
| 2026-11-04 | #67 | #20 | `config.DomainConfiguration` builds the core (transfer service, factory, application service and event consumers); no Spring annotations in the domain or `application` except `@EventListener` (ArchUnit rule) |
| 2026-11-04 | #68 | #21 | Adapter tests without the hexagon: ArchUnit rule over the tests (no input-adapter test with `@SpringBootTest`), event adapter with a test listener in a minimal context and `AccountNumberSequence` contract |
| 2026-11-04 | #69 | #22 | `HexagonalArchitectureTests` (`onionArchitecture()` + port, Spring and security rules) replaces `LayeredArchitectureTests`; each rule validated by breaking it |
| 2026-11-04 | #73 | #23 | Phase 2 closed: layers vs hexagonal comparison (with the same deposit in both phases), final state of `fase-2-hexagonal.md`, AI usage log of phase 2, issues #70-#72 for the next work and tag `v2-hexagonal`; `Hexagonal` stays active |
| 2026-11-04 | #74 | #70 | Documentation in English: ADRs 0001-0005 and 0007, glossary (with a Spanish-term column), OpenAPI descriptions, contracts index, hexagonal diagram labels and the `docs/ia` annexes; two lines of `CLAUDE.md` and `implement-plan` adjusted with the user's approval; T8 of #23 checked |
| 2026-11-05 | #75 | #71 | Graphify measured again on the phase-2 code (`scripts/measure-graphify.mjs`, 30 runs): −32 % on dependency questions with the current rule, "graph first" does not pay off; decision of ADR 0010 confirmed |
| 2026-11-05 | #76 | #72 | Web pending items of #48: segment in the list, transfers labelled "Transferencia a/de" through the new counterpart account of their transactions (domain, application, REST and web), shortened IBAN with non-breaking spaces and transactions newest first; team issue (contract agreed in one round) |
| 2026-11-05 | #80 | #77 | Review leftovers: holder-name rule R21 with its tests, the account rejects its own number as counterpart (R17), segment label shared by web and CLI in `adapter.in.SegmentLabels`; issues #78 and #79 opened |
| 2026-11-06 | #81 | #78 | Every use case runs in a unit of work (`UnitOfWork` port in `application`, `InMemoryUnitOfWork` with one lock): a concurrency test with crossed transfers and deposits failed before (corrupted event list) and passes now; events published once the unit has finished |
| 2026-11-11 | #83 | #79 | Claude Code configuration: `buscador-referencias` sends method questions to Grep (−17 %, ADR 0010), `implement-plan-team` records the team lessons, `CLAUDE.md` mentions the `demo` profile and rule D4; issue #82 opened for the code leftovers |

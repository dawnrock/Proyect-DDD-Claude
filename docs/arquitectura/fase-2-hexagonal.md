# Phase 2 · Hexagonal architecture (`Hexagonal` branch)

![Hexagonal architecture: the domain in the centre, the application around it with its ports, input adapters on the left (REST, web, CLI) and output adapters on the right (persistence, events, tax agency); config connects ports and adapters](img/hexagonal.svg)

This is the **state at the end of phase 2**, marked with the `v2-hexagonal` tag (#23); work continues on the `Hexagonal` branch. How it compares with phase 1: [layers vs hexagonal](comparativa-capas-hexagonal.md).

The domain sits in the centre; the application surrounds it and exposes the input ports. Adapters on the left **use** those ports, and adapters on the right **implement** the output ports the domain declares: on both sides the dependency points inwards. Decided in [ADR 0006](../adr/0006-hexagonal-architecture.md).

## Package structure

At the end of the phase:

```
com.pronoide.ddd
├── adapter
│   ├── in
│   │   ├── cli           DomainModelRunner
│   │   ├── rest          REST API
│   │   └── web           Thymeleaf
│   └── out
│       ├── event         SpringDomainEventPublisher
│       ├── persistence   InMemoryCheckingAccountRepository, InMemoryAccountOwnershipRepository, InMemoryAccountNumberSequence
│       └── taxagency     FakeTaxAgencyIncomeVerifier
├── application           application services and input ports
│   └── listener          domain event consumers
├── config                DomainConfiguration
└── domain
    ├── model             model and output ports
    └── service           MoneyTransferService
```

## Rules

| | Input side | Output side |
| - | - | - |
| Port | `application` (what the application offers) | `domain.model` (what it needs) |
| Adapter | uses the port | implements the port |

- Dependencies always point into the hexagon; adapters do not know each other.
- The core carries no Spring annotations: `config` builds it and hands it its adapters, which it receives by their port types (it imports no class of `adapter.out`). The only exception is `@EventListener` on the event consumers.
- Every output adapter is simulated (in memory / fake), which is enough to show the architecture.
- Checked by `HexagonalArchitectureTests` (ArchUnit, #22), which replaced the provisional `LayeredArchitectureTests` (the phase-1 rules live on in the `Monolithic` branch). Its backbone is `onionArchitecture()`:

  | Ring | Packages |
  | ---- | -------- |
  | Domain model (and output ports) | `domain.model` |
  | Domain services | `domain.service` |
  | Application services (input ports, ownership port, listeners) | `application` |
  | Adapters, one per technology, that do not know each other | `adapter.in.cli`, `adapter.in.rest`, `adapter.in.web`, `adapter.out.persistence`, `adapter.out.event`, `adapter.out.taxagency` |

  The shared security support in the root of `adapter.in`, `config` and the boot class sit outside the rings. Explicit rules complete the onion: output adapters only implement ports of the hexagon and know `application` only through interfaces; input ports (`*Commands`, `*Queries`) are interfaces and input adapters never use `CheckingAccountService`; input adapters only know `DomainException` from the domain; nobody outside `adapter.in` depends on it, and its shared security support does not know `cli`, `rest` or `web`; only `adapter.out` knows the output adapters; nobody depends on `config`; only adapters and `config` depend on Spring web, HTTP, MVC or data; the core does not depend on Spring (except `@EventListener` in `application.listener`); the domain only depends on the JDK (no `org.immutables`) and has no public setters; the ADR 0008 security rules; every class belongs to the hexagon, an adapter or `config`. `DomainIsolationTests` (both phases) and `AdapterTestIsolationTests` (test code, #21) complete them.

## How adapters are tested

Each adapter is tested without the hexagon (#21). `AdapterTestIsolationTests` (ArchUnit over the test code) forbids `@SpringBootTest` in any input-adapter test; the full-context tests live in the root package (`DddLabApplicationTests`, `DemoProfileTests`).

| Adapter | How | Test class |
| ------- | --- | ---------- |
| REST controllers | `@WebMvcTest` with `CheckingAccountCommands`/`CheckingAccountQueries` mocked (`@MockitoBean`) | `AccountControllerTests`, `TransferControllerTests`, `RestExceptionHandlerTests` |
| Web controllers | `@WebMvcTest` with the ports mocked | `AccountWebControllerTests`, `WebSecurityTests` |
| CLI runner | the real application service with in-memory adapters, without Spring | `DomainModelRunnerTests` |
| Persistence | port contracts (`*Contract`) extended by the in-memory adapter's test | `InMemoryCheckingAccountRepositoryTests`, `InMemoryAccountOwnershipRepositoryTests`, `InMemoryAccountNumberSequenceTests`, `InMemoryUnitOfWorkTests` |
| Events | a minimal Spring context with only the adapter and a recording `@EventListener`; plus a unit test with a mocked `ApplicationEventPublisher` | `SpringDomainEventPublisherContextTests`, `SpringDomainEventPublisherTests` |
| Tax agency | the fake on its own | `FakeTaxAgencyIncomeVerifierTests` |

Error paths of every endpoint, all with the ports mocked. With the ports mocked, an account that does not exist and one that belongs to someone else are the same case for the adapter: the service throws `AccountNotFoundException` for both (ADR 0008), and the ownership rule is tested in `CheckingAccountServiceOwnershipTests`:

| Endpoint | 404 | 422 | 400 |
| -------- | --- | --- | --- |
| `POST /api/accounts` | n/a | minimum deposit, duplicate number | field errors (and 403 for non-admins); malformed JSON in `RestExceptionHandlerTests`, shared by every endpoint |
| `GET /api/accounts` | n/a | n/a | n/a (no error path) |
| `GET /api/accounts/{id}` | `AccountNotFoundException` | n/a | malformed UUID |
| `POST /api/accounts/{id}/deposits`, `/withdrawals` | `AccountNotFoundException` | currency mismatch | amount, malformed or unknown currency |
| `POST /api/transfers` | source and target not found | same account, currency mismatch | IBAN, amount |
| Web (detail and its deposit, withdrawal and transfer forms; open account) | custom 404 page | 200: form with the domain message | 200: form with the field error (and 403 page) |

## Class → role

State at the end of the phase: adapters behind input and output ports, domain events published after saving, accounts opened through the factory, demo data as a CLI input adapter, core built in `config` without Spring annotations.

| Class | Package | Role in the architecture |
| ----- | ------- | ------------------------ |
| `AccountController`, `TransferController` | `adapter.in.rest` | Input adapter (REST): HTTP ↔ commands and views; uses the input ports only |
| `RestExceptionHandler` | `adapter.in.rest` | Input adapter: domain and application errors → `ProblemDetail` |
| `OpenAccountRequest`, `MovementRequest`, `TransferRequest`, `*ResponseDef` | `adapter.in.rest` | REST DTOs (records with Bean Validation, Immutables responses); the open-account request carries the holder's NIF |
| `ApiSecurityConfig` | `adapter.in.rest` | Input adapter: security chain of `/api/**` (HTTP Basic) |
| `AccountWebController`, `LoginController`, `AccessDeniedController` | `adapter.in.web` | Input adapter (web): screens and forms; uses the input ports only |
| `MovementForm`, `OpenAccountForm`, `TransferForm` | `adapter.in.web` | Web forms; the open-account form asks for the holder's NIF |
| `WebExceptionHandler`, `WebModelAdvice`, `WebFormat` | `adapter.in.web` | Web support: 404 page, common model attributes, formatting (amounts, shortened IBAN, segment label) |
| `WebSecurityConfig` | `adapter.in.web` | Input adapter: fallback security chain (form login) |
| `SecuritySupport`, `CurrentCaller`, `SecurityUsersProperties` | `adapter.in` | Support shared by the input adapters: authenticated user → `Caller` |
| `SegmentLabels` | `adapter.in` | Support shared by the input adapters: Spanish label of a segment code, used by the web (`WebFormat.segment`) and the CLI trace (#77) |
| `InMemoryCheckingAccountRepository` | `adapter.out.persistence` | Output adapter (simulated persistence, package-private): implements `CheckingAccountRepository` |
| `SpringDomainEventPublisher` | `adapter.out.event` | Output adapter (package-private): implements `DomainEventPublisher` over Spring's `ApplicationEventPublisher` |
| `InMemoryAccountNumberSequence` | `adapter.out.persistence` | Output adapter (simulated, package-private): implements `AccountNumberSequence`, valid consecutive Spanish IBANs |
| `FakeTaxAgencyIncomeVerifier` | `adapter.out.taxagency` | Output adapter (simulated, package-private): implements `IncomeVerifier` with a fixed table |
| `InMemoryAccountOwnershipRepository` | `adapter.out.persistence` | Output adapter (package-private): implements `AccountOwnershipRepository`, which user owns each account (ADR 0008) |
| `InMemoryUnitOfWork` | `adapter.out.persistence` | Output adapter (simulated, package-private): implements `UnitOfWork` with one fair lock for every use case (#78) |
| `CheckingAccountService` | `application` | Application service: implements `CheckingAccountCommands` and `CheckingAccountQueries` (use cases and account ownership); uses the output ports only, opens accounts through `CheckingAccountFactory` and runs every use case inside a `UnitOfWork` and publishes the domain events of the saved accounts once the unit has finished (#78). Keeps its name (not `CheckingAccountApplicationService`): nobody outside `application` sees it. No Spring annotations: built in `config` |
| `CheckingAccountCommands`, `CheckingAccountQueries` | `application` | Input ports: use cases that change accounts (open, deposit, withdraw, transfer) and that query them (list, detail) |
| `AccountOwnershipRepository` | `application` | Output port: account ownership. In `application`, not `domain.model`, because the user's identity never reaches the domain (ADR 0008) |
| `UnitOfWork` | `application` | Output port: runs a use case as one unit (load, change, save); in `application` because the boundary of a use case is an application concern (#78) |
| `*CommandDef`, `*SummaryDef`, `AccountDetailsDef`, `CallerDef`, `ApplicationValue`, `CommandValidation` | `application` | Commands, views and `Caller` (Immutables) and their validation |
| `AuditLogListener`, `CustomerNotificationListener` | `application.listener` | Domain event consumers: audit log and simulated customer notification. Declared in `config`; they keep `@EventListener`, the only Spring dependency allowed in the core (events reach them through Spring) |
| `NotFoundException`, `AccountNotFoundException`, `TargetAccountNotFoundException`, `ForbiddenOperationException`, `OperationNotAllowedException` | `application` | Application errors |
| `DomainModelRunner` | `adapter.in.cli` | Input adapter (CLI): with the `demo` profile, opens the demo accounts and runs a deposit, two withdrawals (approved and rejected) and a transfer through `CheckingAccountCommands`, with a `[demo]` trace per use case |
| `CheckingAccount` | `domain.model` | Aggregate root; records its domain events (`pullDomainEvents`); `open` is package-private, used only by the factory |
| `CheckingAccountFactory` | `domain.model` | Factory: number + segment from the verified income (R20); built in `config` |
| `AccountNumberSequence`, `IncomeVerifier` | `domain.model` | Output ports: account numbering and income verification |
| `DomainEvent`, `CheckingAccountOpened`, `MoneyDeposited`, `MoneyWithdrawn`, `WithdrawalRejected` | `domain.model` | Domain events (`record`) |
| `DomainEventPublisher` | `domain.model` | Output port: publishes domain events |
| `CheckingAccountRepository` | `domain.model` | Output port: repository of the aggregate |
| `AccountHolder`, `Transaction` | `domain.model` | Entities |
| `AccountNumber`, `MonetaryAmount`, `TaxId` | `domain.model` | Value objects |
| `CustomerSegment`, `TransactionStatus`, `TransactionType` | `domain.model` | Domain enumerations; the segment is decided by the factory |
| `DomainException` and its subclasses (`CurrencyMismatchException`, `DuplicateAccountNumberException`, `InsufficientInitialDepositException`, `SameAccountTransferException`) | `domain.model` | Business rule violations (422) |
| `MoneyTransferService` | `domain.service` | Domain service (transfers, R17-R19); built in `config` |
| `DomainConfiguration` | `config` | `@Configuration` that builds the core (`MoneyTransferService`, `CheckingAccountFactory`, `CheckingAccountService`, the listeners) and hands it the output adapters by their ports |
| `DddLabApplication` | root | Spring Boot entry point |

## Issues of the phase

| Issue | Title | PR |
| ----- | ----- | -- |
| #14 | Reorganise the packages for the hexagonal architecture | #61 |
| #15 | Output port `CheckingAccountRepository` and in-memory adapter | #62 |
| #16 | Input ports `CheckingAccountCommands` and `CheckingAccountQueries` | #63 |
| #17 | Domain events: publishing and consumers | #64 |
| #18 | Account factory with numbering and income verification ports (back + web, *team*) | #65 |
| #19 | Command-line input adapter | #66 |
| #20 | Wire ports and adapters in `config` without Spring in the core | #67 |
| #21 | Adapter tests without the hexagon | #68 |
| #22 | ArchUnit rules for the hexagonal architecture | #69 |
| #23 | Close phase 2: layers vs hexagonal comparison and `v2-hexagonal` tag | #73 |

Issues marked *team* were implemented with `/implement-plan-team` (contract first, ADR 0007).

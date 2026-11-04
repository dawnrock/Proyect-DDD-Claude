# Phase 2 · Hexagonal architecture (`Hexagonal` branch)

![Hexagonal architecture: the domain in the centre, the application around it with its ports, input adapters on the left (REST, web, CLI) and output adapters on the right (persistence, events, tax agency); config connects ports and adapters](img/hexagonal.svg)

The domain sits in the centre; the application surrounds it and exposes the input ports. Adapters on the left **use** those ports, and adapters on the right **implement** the output ports the domain declares: on both sides the dependency points inwards. Decided in [ADR 0006](../adr/0006-hexagonal-architecture.md).

## Package structure

Target at the end of the phase:

```
com.pronoide.ddd
├── adapter
│   ├── in
│   │   ├── cli           DomainModelRunner
│   │   ├── rest          REST API
│   │   └── web           Thymeleaf
│   └── out
│       ├── event         SpringDomainEventPublisher
│       ├── persistence   InMemoryCheckingAccountRepository, InMemoryAccountNumberSequence
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
- The core carries no Spring annotations: `config` builds it and hands it its adapters.
- Every output adapter is simulated (in memory / fake), which is enough to show the architecture.
- Checked by `HexagonalArchitectureTests` (ArchUnit, #22). Until then, `LayeredArchitectureTests` with its patterns adjusted to the new packages (#14).

## Class → role

State after #19 (adapters behind input and output ports, domain events published after saving, accounts opened through the factory, demo data as a CLI input adapter). The last column says which issue changes the class's role.

| Class | Package | Role in the architecture | Changes in |
| ----- | ------- | ------------------------ | ---------- |
| `AccountController`, `TransferController` | `adapter.in.rest` | Input adapter (REST): HTTP ↔ commands and views; uses the input ports only | — |
| `RestExceptionHandler` | `adapter.in.rest` | Input adapter: domain and application errors → `ProblemDetail` | — |
| `OpenAccountRequest`, `MovementRequest`, `TransferRequest`, `*ResponseDef` | `adapter.in.rest` | REST DTOs (records with Bean Validation, Immutables responses); the open-account request carries the holder's NIF | — |
| `ApiSecurityConfig` | `adapter.in.rest` | Input adapter: security chain of `/api/**` (HTTP Basic) | — |
| `AccountWebController`, `LoginController`, `AccessDeniedController` | `adapter.in.web` | Input adapter (web): screens and forms; uses the input ports only | — |
| `MovementForm`, `OpenAccountForm`, `TransferForm` | `adapter.in.web` | Web forms; the open-account form asks for the holder's NIF | — |
| `WebExceptionHandler`, `WebModelAdvice`, `WebFormat` | `adapter.in.web` | Web support: 404 page, common model attributes, amount formatting | — |
| `WebSecurityConfig` | `adapter.in.web` | Input adapter: fallback security chain (form login) | — |
| `SecuritySupport`, `CurrentCaller`, `SecurityUsersProperties` | `adapter.in` | Support shared by the input adapters: authenticated user → `Caller` | — |
| `InMemoryCheckingAccountRepository` | `adapter.out.persistence` | Output adapter (simulated persistence, package-private): implements `CheckingAccountRepository` | #20: wired in `config` |
| `SpringDomainEventPublisher` | `adapter.out.event` | Output adapter (package-private): implements `DomainEventPublisher` over Spring's `ApplicationEventPublisher` | #20: wired in `config` |
| `InMemoryAccountNumberSequence` | `adapter.out.persistence` | Output adapter (simulated, package-private): implements `AccountNumberSequence`, valid consecutive Spanish IBANs | #20: wired in `config` |
| `FakeTaxAgencyIncomeVerifier` | `adapter.out.taxagency` | Output adapter (simulated, package-private): implements `IncomeVerifier` with a fixed table | #20: wired in `config` |
| `InMemoryAccountOwnershipRepository` | `adapter.out.persistence` | Output adapter (package-private): implements `AccountOwnershipRepository`, which user owns each account (ADR 0008) | #20: wired in `config` |
| `CheckingAccountService` | `application` | Application service: implements `CheckingAccountCommands` and `CheckingAccountQueries` (use cases and account ownership); uses the output ports only, opens accounts through `CheckingAccountFactory` (which it builds from its two ports) and publishes each account's domain events after saving it. Keeps its name (not `CheckingAccountApplicationService`): nobody outside `application` sees it | #20 built in `config` |
| `CheckingAccountCommands`, `CheckingAccountQueries` | `application` | Input ports: use cases that change accounts (open, deposit, withdraw, transfer) and that query them (list, detail) | — |
| `AccountOwnershipRepository` | `application` | Output port: account ownership. In `application`, not `domain.model`, because the user's identity never reaches the domain (ADR 0008) | — |
| `*CommandDef`, `*SummaryDef`, `AccountDetailsDef`, `CallerDef`, `ApplicationValue`, `CommandValidation` | `application` | Commands, views and `Caller` (Immutables) and their validation | — |
| `AuditLogListener`, `CustomerNotificationListener` | `application.listener` | Domain event consumers (`@EventListener`, `@Slf4j`): audit log and simulated customer notification | — |
| `NotFoundException`, `AccountNotFoundException`, `TargetAccountNotFoundException`, `ForbiddenOperationException`, `OperationNotAllowedException` | `application` | Application errors | — |
| `DomainModelRunner` | `adapter.in.cli` | Input adapter (CLI): with the `demo` profile, opens the demo accounts and runs a deposit, two withdrawals (approved and rejected) and a transfer through `CheckingAccountCommands`, with a `[demo]` trace per use case | — |
| `CheckingAccount` | `domain.model` | Aggregate root; records its domain events (`pullDomainEvents`); `open` is package-private, used only by the factory | — |
| `CheckingAccountFactory` | `domain.model` | Factory: number + segment from the verified income (R20) | #20: built in `config` |
| `AccountNumberSequence`, `IncomeVerifier` | `domain.model` | Output ports: account numbering and income verification | — |
| `DomainEvent`, `CheckingAccountOpened`, `MoneyDeposited`, `MoneyWithdrawn`, `WithdrawalRejected` | `domain.model` | Domain events (`record`) | — |
| `DomainEventPublisher` | `domain.model` | Output port: publishes domain events | — |
| `CheckingAccountRepository` | `domain.model` | Output port: repository of the aggregate | — |
| `AccountHolder`, `Transaction` | `domain.model` | Entities | — |
| `AccountNumber`, `MonetaryAmount`, `TaxId` | `domain.model` | Value objects | — |
| `CustomerSegment`, `TransactionStatus`, `TransactionType` | `domain.model` | Domain enumerations; the segment is decided by the factory | — |
| `DomainException` and its subclasses (`CurrencyMismatchException`, `DuplicateAccountNumberException`, `InsufficientInitialDepositException`, `SameAccountTransferException`) | `domain.model` | Business rule violations (422) | — |
| `MoneyTransferService` | `domain.service` | Domain service (transfers, R17-R19) | #20: built in `config` |
| `DddLabApplication` | root | Spring Boot entry point | — |

Classes that arrive later: `DomainConfiguration` (#20).

# Fase 2 · Arquitectura hexagonal (rama `Hexagonal`)


![Arquitectura hexagonal: el dominio en el centro, la aplicación alrededor con sus puertos, los adaptadores de entrada a la izquierda (REST, web, CLI) y los de salida a la derecha (persistencia, eventos, agencia tributaria); config conecta puertos y adaptadores](img/hexagonal.svg)

El dominio ocupa el centro; la aplicación lo rodea y expone los puertos de entrada. Los adaptadores de la izquierda **usan** esos puertos y los de la derecha **implementan** los puertos de salida que declara el dominio: en los dos lados la dependencia apunta hacia dentro.

## Estructura de paquetes

```
com.pronoide.ddd
├── adapter
│   ├── in
│   │   ├── cli           DomainModelRunner
│   │   ├── rest          API REST
│   │   └── web           Thymeleaf
│   └── out
│       ├── event         SpringDomainEventPublisher
│       ├── persistence   InMemoryCheckingAccountRepository, InMemoryAccountNumberSequence
│       └── taxagency     FakeTaxAgencyIncomeVerifier
├── application           servicios de aplicación y puertos de entrada
│   └── listener          consumidores de eventos de dominio
├── config                DomainConfiguration
└── domain
    ├── model             modelo y puertos de salida
    └── service           MoneyTransferService
```

## Reglas

| | Lado de entrada | Lado de salida |
| - | - | - |
| Puerto | `application` (lo que la aplicación ofrece) | `domain.model` (lo que necesita) |
| Adaptador | usa el puerto | implementa el puerto |

- Las dependencias apuntan siempre hacia el hexágono; los adaptadores no se conocen entre sí.
- El núcleo no lleva anotaciones de Spring: `config` lo construye y le entrega sus adaptadores.
- Todos los adaptadores de salida son simulados (en memoria / ficticios), lo que basta para demostrar la arquitectura.
- Verificado por `HexagonalArchitectureTests` (ArchUnit).

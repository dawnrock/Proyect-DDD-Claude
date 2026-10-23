---
name: hexagonal-adapter
description: Crea un puerto (de entrada o de salida) y su adaptador en la arquitectura hexagonal (rama Hexagonal, fase 2), con su test y su cableado en config. Úsala cuando se pida añadir o cambiar un puerto, un adaptador de entrada (REST, web, CLI) o de salida (persistencia, eventos, servicio externo simulado), o conectar el hexágono con una tecnología. No aplica en la rama Monolithic.
argument-hint: <in|out> <nombre del puerto o adaptador> [tecnología]
allowed-tools: Read, Grep, Glob, Edit, Write, Bash(git branch:*), Bash(./mvnw:*)
---

Crea el puerto/adaptador pedido: $ARGUMENTS

## Antes de empezar

- Comprueba la rama con `git branch --show-current`. Si no es `Hexagonal` ni una rama de trabajo creada desde ella, detente: en la fase 1 (capas) no hay puertos.
- Repasa en `docs/arquitectura/fase-2-hexagonal.md` la estructura de paquetes y en qué lado del hexágono va el elemento.

## Reglas

| | Lado de entrada (driving) | Lado de salida (driven) |
| - | - | - |
| Quién inicia | El exterior (usuario, REST, CLI, test) | La aplicación necesita algo de fuera |
| Puerto | Interfaz en `application` (`CheckingAccountCommands`, `CheckingAccountQueries`) | Interfaz en `domain.model` (`CheckingAccountRepository`, `DomainEventPublisher`, `IncomeVerifier`) |
| Adaptador | `adapter.in.<tecnología>`: **usa** el puerto y traduce la petición | `adapter.out.<tecnología>`: **implementa** el puerto |
| Datos | Recibe/devuelve DTOs propios (Immutables o `record`); nunca expone agregados | Traduce entre el modelo y la tecnología |

- Las dependencias apuntan siempre hacia el hexágono; los adaptadores no se conocen entre sí.
- Los adaptadores de salida son **package-private** salvo lo que necesite `config`.
- El cableado puerto → adaptador está en `config` (`DomainConfiguration`), no en el dominio.
- Los adaptadores simulados (en memoria, agencia tributaria ficticia) son válidos: llevan el sufijo `InMemory` o `Fake` y lo indican en su Javadoc.

## Procedimiento

1. Crea el puerto a partir de `templates/Port.java.template` en el paquete que indica la tabla.
2. Crea el adaptador con `templates/OutputAdapter.java.template` o `templates/RestInputAdapter.java.template`.
3. Escribe el test del adaptador **sin el hexágono**: los de entrada con `@WebMvcTest` y el puerto simulado con Mockito (`templates/RestInputAdapterTests.java.template`); los de salida contra el contrato del puerto.
4. Cablea en `config` si hace falta y ejecuta `./mvnw verify`: `HexagonalArchitectureTests` debe pasar.

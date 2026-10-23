# Plan: Persistencia en memoria de las cuentas corrientes

- **Issue:** [#4](https://github.com/dawnrock/Proyect-DDD-Claude/issues/4)
- **Tipo:** feature
- **Fase:** 1 · Monolito en capas
- **Rama base:** `Monolithic`
- **Fecha:** 2026-10-23
- **Estado:** completado
- **Estimación total:** ~30 min (4 tareas)

## Objetivo

Que la aplicación pueda guardar y recuperar cuentas corrientes en la capa `persistence`, con un repositorio en memoria (simulado), para que los casos de uso de #5, #6 y #7 tengan dónde cargar y guardar el agregado `CheckingAccount`. Siguiendo el ADR 0004, es una **clase concreta sin interfaz**: en la fase 1 no hay puertos.

### Fuera de alcance

- La interfaz `CheckingAccountRepository` (puerto de salida): llega en la fase 2 (#15).
- JPA o H2: la persistencia es simulada.
- Casos de uso que usen el repositorio: llegan en #5.

## Contexto

- `CheckingAccount` (raíz del agregado) se identifica por `id()` (`UUID`) y expone `accountNumber()` (`AccountNumber`, un `record` con igualdad por valor). Los movimientos (`Transaction`) viven dentro del agregado, así que hay **un único repositorio**, el del agregado (reglas DDD, `reglas-ddd.md`).
- Reglas de dependencia de `docs/arquitectura/fase-1-capas.md`: **D2** (`persistence` solo depende de `domain`) y **D1** (`domain` no depende de `persistence`). La regla de ArchUnit llega en la #12; aquí se comprueban con una búsqueda de imports.
- `persistence` puede usar Spring (`@Repository`): la prohibición de Spring es solo para `domain`.
- No toca la API REST.

| Capa / paquete | Archivo | Cambio |
| -------------- | ------- | ------ |
| `persistence` | `src/main/java/com/pronoide/ddd/persistence/InMemoryCheckingAccountRepository.java` | Nuevo |
| tests | `src/test/java/com/pronoide/ddd/persistence/InMemoryCheckingAccountRepositoryTests.java` | Nuevo |
| docs | `docs/dominio/glosario.md` | "Repositorio de cuentas" pasa de previsto a existente |

## Tareas

### [x] T1 — Guardar y recuperar por id (~10 min)

- **Capa:** persistence
- **Archivos:** `persistence/InMemoryCheckingAccountRepository.java`, `persistence/InMemoryCheckingAccountRepositoryTests.java`
- **Depende de:** —
- **Qué hacer:**
  1. **Rojo:** tests que guardan una cuenta abierta con `CheckingAccount.open(...)` y la recuperan con `findById(id)`; que `findById` de un id inexistente devuelve `Optional.empty()`; y que volver a guardar la misma cuenta la sustituye, sin duplicarla.
  2. **Verde:** clase `@Repository` con un `ConcurrentHashMap<UUID, CheckingAccount>`, `save(CheckingAccount)` (devuelve la cuenta guardada) y `findById(UUID)`. Rechaza argumentos nulos con `Objects.requireNonNull`.
- **Criterios de aceptación:**
  - [x] `./mvnw test -Dtest=InMemoryCheckingAccountRepositoryTests` pasa (guardar y recuperar, inexistente → vacío, guardar dos veces → una sola cuenta).
  - [x] `./mvnw verify` en verde.

### [x] T2 — Buscar por número de cuenta y número único (~10 min)

- **Capa:** persistence
- **Archivos:** los mismos que T1
- **Depende de:** T1
- **Qué hacer:**
  1. **Rojo:** tests de `findByAccountNumber(AccountNumber)`: encuentra la cuenta y la normalización del IBAN no afecta (`"ES91 2100…"` y `"ES912100…"` son el mismo número); número inexistente → `Optional.empty()`; guardar **otra** cuenta con un número ya usado lanza `IllegalStateException` con un mensaje en español. Es el equivalente a un índice único en una base de datos.
  2. **Verde:** búsqueda por `accountNumber()` y comprobación de unicidad en `save`.
- **Criterios de aceptación:**
  - [x] Los tests nuevos pasan junto con los de T1.
  - [x] Guardar de nuevo la misma cuenta (mismo id y número) no se considera duplicado.

### [x] T3 — Listar todas las cuentas en orden estable (~5 min)

- **Capa:** persistence
- **Archivos:** los mismos que T1
- **Depende de:** T1
- **Qué hacer:**
  1. **Rojo:** test de `findAll()`: devuelve todas las cuentas ordenadas por número de cuenta, y la lista devuelta no se puede modificar desde fuera.
  2. **Verde:** `List.copyOf` de los valores ordenados por `accountNumber().value()`.
- **Criterios de aceptación:**
  - [x] Con el repositorio vacío devuelve una lista vacía; con tres cuentas, las devuelve ordenadas.
  - [x] `findAll().clear()` lanza `UnsupportedOperationException` y no vacía el repositorio.

### [x] T4 — Glosario: el repositorio de cuentas ya existe (~5 min)

- **Capa:** docs
- **Archivos:** `docs/dominio/glosario.md`
- **Depende de:** T3
- **Qué hacer:**
  1. Pasar "Repositorio de cuentas" de "Términos previstos" a una sección nueva "Persistencia", describiendo sus operaciones y la regla de número único.
- **Criterios de aceptación:**
  - [x] `InMemoryCheckingAccountRepository` ya no aparece como previsto.

### [x] T5 — Correcciones de la revisión de revisor-ddd (~5 min)

- **Capa:** persistence · docs
- **Archivos:** `persistence/InMemoryCheckingAccountRepositoryTests.java`, `docs/dominio/glosario.md`
- **Depende de:** T4
- **Qué hacer:**
  1. El test "guardar dos veces no la duplica" pasaba aunque se quitase el segundo `save` (el repositorio guarda referencias): ahora comprueba `findAll`, y un test aparte fija que se guarda la propia cuenta.
  2. Tests de los argumentos nulos de `save`, `findById` y `findByAccountNumber`.
  3. Nota en el glosario: el caso de uso de #5 debe convertir el número duplicado en una excepción de dominio.
- **Criterios de aceptación:**
  - [x] Los tests nuevos fallan con el código saboteado (id aleatorio al guardar, sin `requireNonNull`) y pasan con el código real.
  - [x] `./mvnw verify` en verde.

## Criterios de aceptación globales

**Específicos:**

- [x] Tests JUnit: guardar y recuperar por id y por número de cuenta; `findById` de una cuenta inexistente devuelve `Optional.empty()`.
- [x] `domain` no depende de `persistence` (ningún `import com.pronoide.ddd.persistence` en `src/main/java/com/pronoide/ddd/domain`), y `persistence` solo importa de `domain` (D1 y D2).
- [x] `./mvnw verify` en verde.

**Fijos:**

- [x] Todas las tareas y sus criterios están marcados como cumplidos.
- [x] `./mvnw verify` termina en `BUILD SUCCESS` (JUnit + ArchUnit).
- [x] `./mvnw spring-boot:run` arranca y `GET /actuator/health` responde `{"status":"UP"}`.
- [x] Se respeta la arquitectura de la fase (D1 y D2 de `fase-1-capas.md`); la regla de ArchUnit completa llega en la #12.
- [x] El dominio no depende de Spring, `jakarta` ni `org.immutables`, y no usa `@Data`, `@Setter`, `@Builder` ni `@AllArgsConstructor` (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` sin errores).
- [x] Los tests nuevos siguen `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` en español, sin Spring ni mocks).
- [x] No se añaden dependencias.
- [x] Si se toca la API: no aplica.
- [x] Si aparece un término de negocio nuevo, está en `docs/dominio/glosario.md` (T4).

## Riesgos y preguntas abiertas

- **Para #5 (revisión):** `IllegalStateException` por número duplicado no es una `DomainException`; el caso de uso de abrir cuenta debe comprobar antes el número y lanzar una excepción de dominio.

- **El repositorio guarda referencias, no copias.** Si un caso de uso modifica una cuenta y olvida llamar a `save`, el cambio se vería igualmente, cosa que una base de datos real no haría. Se acepta porque es simulado, pero los tests de aplicación (#5 y siguientes) deben comprobar que se llama a `save`. Se documenta en el Javadoc de la clase.
- **El número de cuenta único es una regla de persistencia.** No es una regla del agregado, porque una cuenta no puede saber si existe otra con su número. Si en el futuro se genera el número con una factoría (#18, fase 2), la factoría garantizará la unicidad y el repositorio la seguirá vigilando.

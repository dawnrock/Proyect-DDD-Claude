# Plan: Documentar el glosario y la arquitectura en capas de la fase 1

- **Issue:** [#3](https://github.com/dawnrock/Proyect-DDD-Claude/issues/3)
- **Tipo:** feature (docs)
- **Fase:** 1 · Monolito en capas
- **Rama base:** `Monolithic`
- **Fecha:** 2026-10-23
- **Estado:** en curso
- **Estimación total:** ~30 min (3 tareas)

## Objetivo

Dejar por escrito, antes de escribir código de la fase 1, el lenguaje ubicuo del dominio actual y el diseño objetivo de la arquitectura en capas. Así, las issues #4 a #12 parten de un vocabulario y unas reglas de dependencia acordados, y la regla de ArchUnit de la #12 tiene una especificación contra la que comprobarse.

### Fuera de alcance

- Cambiar código Java. Si al documentar aparece una incoherencia en el dominio, se anota en "Riesgos" y se abre una issue aparte.
- La arquitectura hexagonal (`docs/arquitectura/fase-2-hexagonal.md`, ADR 0006): corresponde a la fase 2.

## Contexto

- `docs/dominio/glosario.md` ya tiene un borrador con 10 términos. Al contrastarlo con las 11 clases de `domain.model`:
  - **Faltan:** `TransactionStatus` (`PENDING`, `APPROVED`, `REJECTED`), `TransactionType` como término propio, `DomainException` y sus subclases `InsufficientInitialDepositException` y `CurrencyMismatchException`.
  - **Está incompleto:** `AccountNumber` exige `ES` + 22 dígitos, quita espacios y pasa a mayúsculas. **No valida el dígito de control.**
  - "Transferencia" aparece como si existiera, pero llega con la #7: hay que marcarlo como término previsto.
- `docs/arquitectura/fase-1-capas.md` tiene un borrador con el diagrama y la tabla de paquetes. Falta la tabla de dependencias permitidas y prohibidas que traducirá la #12, el reparto de responsabilidades con un ejemplo de flujo y la correspondencia de cada issue con su capa.
- `docs/adr/0004-arquitectura-en-capas.md` no existe todavía. `docs/adr/README.md` la tiene como pendiente.
- Capítulos del curso de DDD que hay que citar: **14** (arquitectura basada en capas), **17** (antipatrón *layer cake*) y **18** (antipatrón *anemic translation*, con **18.1** *bypass* entre capas).

| Capa / paquete | Archivo | Cambio |
| -------------- | ------- | ------ |
| docs | `docs/dominio/glosario.md` | Completar términos, reglas de negocio con su test y términos previstos |
| docs | `docs/arquitectura/fase-1-capas.md` | Dependencias permitidas y prohibidas, diagrama de secuencia, correspondencia issue → capa |
| docs | `docs/adr/0004-arquitectura-en-capas.md`, `docs/adr/README.md` | ADR nuevo e índice actualizado |

## Tareas

### [x] T1 — Completar el glosario con el dominio actual (~10 min)

- **Capa:** docs
- **Archivos:** `docs/dominio/glosario.md`
- **Depende de:** —
- **Qué hacer:**
  1. Añadir una fila por cada clase de `domain.model` que falte: `TransactionType`, `TransactionStatus`, `DomainException`, `InsufficientInitialDepositException` y `CurrencyMismatchException`, con su tipo DDD (enumerado o excepción de dominio).
  2. Corregir `AccountNumber`: IBAN español `ES` + 22 dígitos, normalizado sin espacios y en mayúsculas, sin validar el dígito de control.
  3. Añadir la sección **"Reglas de negocio"**, numerada (`R1`, `R2`, …), con cada regla y el test que la demuestra. Por ejemplo, R1 sería el depósito inicial mínimo de 50,00 €, demostrado por `CheckingAccountTests`. Las reglas se sacan de `CheckingAccount`, `Transaction`, `MonetaryAmount`, `AccountNumber` y `AccountHolder`.
  4. Añadir la sección **"Términos previstos"** (transferencia → #7, commands y vistas de aplicación → #5…), separada de lo que ya existe.
- **Criterios de aceptación:**
  - [x] Cada una de las 11 clases de `src/main/java/com/pronoide/ddd/domain/model/` aparece en el glosario. Se comprueba buscando el nombre de cada archivo `.java` en el glosario.
  - [x] Cada regla `Rn` cita un test existente en `src/test/java/com/pronoide/ddd/domain/model/`.

### [x] T2 — Completar el diseño de la arquitectura en capas (~10 min)

- **Capa:** docs
- **Archivos:** `docs/arquitectura/fase-1-capas.md`
- **Depende de:** T1
- **Qué hacer:**
  1. Mantener el diagrama Mermaid de las cuatro capas (`presentation`, `application`, `domain`, `persistence`) y revisar que las flechas muestran el sentido de las dependencias.
  2. Añadir la tabla **"Dependencias"**: qué paquete puede depender de cuál y qué está prohibido. Ejemplos: `domain` no depende de nadie, `persistence` no conoce `application` ni `presentation`, y los controladores no usan `persistence`. Es la especificación de la regla de ArchUnit de la #12.
  3. Añadir un diagrama de secuencia Mermaid de un caso de uso, el ingreso (`POST /api/accounts/{id}/deposits`, ruta definida en la #8), que recorra las capas y muestre dónde vive cada responsabilidad (validar, cargar, regla de negocio, guardar, traducir el error).
  4. Añadir la tabla **"Issues de la fase"**: issue → capa y clases que introduce (#4 a #12).
- **Criterios de aceptación:**
  - [ ] El diagrama muestra las cuatro capas y el sentido de las dependencias, y se renderiza en la vista de GitHub de la PR.
  - [x] Cada regla de la tabla "Dependencias" se puede expresar como una regla de ArchUnit (se indica el paquete de origen y de destino).

### [x] T3 — ADR 0004 · Arquitectura en capas (~10 min)

- **Capa:** docs
- **Archivos:** `docs/adr/0004-arquitectura-en-capas.md`, `docs/adr/README.md`
- **Depende de:** T2
- **Qué hacer:**
  1. Escribir el ADR con el formato del índice (contexto, decisión, alternativas y consecuencias):
     - **Decisión:** capas con clases concretas y sin puertos; `application` puede usar `persistence` directamente (el *bypass* del cap. 18.1), para no caer en un *layer cake* (cap. 17).
     - **Alternativas:** capas estrictas (`application → domain → persistence`), descartada porque obliga a que el dominio conozca la persistencia o a reenviar llamadas sin aportar nada. Hexagonal desde el principio, descartada porque es la fase 2 y se quiere comparar.
     - **Consecuencias:** riesgo de *anemic translation* (cap. 18) entre DTOs, vistas y modelo, que se mitiga con el lenguaje ubicuo del glosario. La regla de ArchUnit llega en la #12.
  2. Enlazar el glosario, `fase-1-capas.md` y los capítulos 14, 17 y 18 del curso.
  3. Actualizar la fila 0004 de `docs/adr/README.md`: enlace y estado "Aceptada".
- **Criterios de aceptación:**
  - [x] El ADR cita los capítulos 14, 17 y 18 (y el 18.1) del curso de DDD.
  - [x] `docs/adr/README.md` enlaza el ADR 0004 con estado "Aceptada".

### [x] T4 — Correcciones de la revisión de revisor-ddd (~5 min)

- **Capa:** docs
- **Archivos:** `docs/arquitectura/fase-1-capas.md`, `docs/dominio/glosario.md`, `docs/adr/0004-arquitectura-en-capas.md`
- **Depende de:** T3
- **Qué hacer:**
  1. R3 lanza `IllegalArgumentException` (no `DomainException`): corregir el diagrama de secuencia y documentar la traducción de errores (400 / 422 / 404).
  2. D4 y D6 se contradecían: fusionarlas en D4 (presentation solo conoce las excepciones de dominio), flecha punteada en el diagrama y referencias D1-D5.
  3. Lenguaje ubicuo: "transacción" y "reintegro" como sinónimos heredados de "movimiento" y "retirada", con su alineación pendiente.
- **Criterios de aceptación:**
  - [x] Ningún documento atribuye R3 a `DomainException`.
  - [x] No hay dos reglas de dependencia incompatibles; el diagrama muestra la dependencia de presentation con las excepciones.
  - [x] Los sinónimos heredados están documentados.

## Criterios de aceptación globales

**Específicos:**

- [ ] Cada clase de `domain.model` tiene su término en el glosario.
- [ ] El diagrama muestra las cuatro capas y el sentido de las dependencias.
- [ ] El ADR enlaza con los capítulos 14, 17 y 18 del curso (capas, layer cake, anemic translation).
- [ ] Todos los enlaces relativos entre `glosario.md`, `fase-1-capas.md`, el ADR 0004 y `CLAUDE.md` apuntan a archivos que existen.

**Fijos:**

- [ ] Todas las tareas y sus criterios están marcados como cumplidos.
- [ ] `./mvnw verify` termina en `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` arranca y `GET /actuator/health` responde `{"status":"UP"}`: no aplica (no se toca código).
- [ ] Se respeta la arquitectura de la fase: no aplica (solo documentación).
- [ ] El dominio no depende de Spring, `jakarta` ni `org.immutables`, y no usa `@Data`, `@Setter`, `@Builder` ni `@AllArgsConstructor` (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` sin errores).
- [ ] Los tests nuevos siguen `src/test/CLAUDE.md`: no aplica (no hay tests nuevos).
- [ ] No se añaden dependencias fuera de las previstas: no aplica.
- [ ] Si se toca la API: no aplica.
- [ ] Si aparece un término de negocio nuevo, está en `docs/dominio/glosario.md`.

## Riesgos y preguntas abiertas

- **`AccountNumber` no valida el dígito de control del IBAN.** Se documenta como comportamiento actual. Si se quiere validarlo, hace falta una issue aparte con su test, porque es una regla nueva.

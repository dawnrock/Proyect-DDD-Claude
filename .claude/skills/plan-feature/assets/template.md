# Plan: <título breve>

- **Issue:** [#N](<URL de la issue>)
- **Tipo:** feature | fix | refactor
- **Fase:** 1 · Monolito en capas | 2 · Arquitectura hexagonal
- **Rama base:** `Monolithic` | `Hexagonal`
- **Fecha:** AAAA-MM-DD
- **Estado:** borrador
- **Estimación total:** ~N min (N tareas)

## Objetivo

<!-- Qué se quiere conseguir y por qué, en 2-4 frases. Fix: comportamiento actual vs. esperado. Refactor: qué mejora y qué NO debe cambiar. -->

### Fuera de alcance

- ...

## Contexto

<!-- Archivos y capas afectados, bloques DDD implicados (VO, entidad, agregado, evento, factoría, servicio, puerto, adaptador) y decisiones técnicas. -->

| Capa / paquete | Archivo | Cambio |
| -------------- | ------- | ------ |
| `domain.model` | `src/main/java/com/pronoide/ddd/domain/model/...` | ... |

## Tareas

<!-- Cada tarea: 5-10 min, una capa, 1-2 archivos + test, deja ./mvnw verify en verde. De dentro hacia fuera.
     Si la issue toca back y web: empieza por "T0 — Contrato" (Lado: ambos, ver implement-plan-team/assets/contract-template.md),
     añade **Lado:** a cada tarea y haz que las de back y web dependan solo de T0. -->

### [ ] T0 — Contrato (~10 min) <!-- solo si toca back y web; si no, bórrala -->

- **Lado:** ambos
- **Capa:** application · docs
- **Archivos:** `docs/contracts/<slug>.md`, tipos del contrato en `application`, fragmento de `docs/api/openapi.yaml`
- **Depende de:** —
- **Qué hacer:**
  1. Redactar el contrato y crear los tipos sin lógica; backend-dev y web-dev lo revisan y acuerdan.
- **Criterios de aceptación:**
  - [ ] Contrato acordado y congelado en un commit; `./mvnw verify` compila contra él.

### [ ] T1 — <título> (~5 min)

- **Lado:** back | web | ambos <!-- solo si el plan tiene T0 · Contrato -->
- **Capa:** domain | application | persistence | presentation | adapter.in | adapter.out | config | tests | docs | build
- **Archivos:** `src/...`
- **Depende de:** —
- **Qué hacer:**
  1. ...
- **Criterios de aceptación:**
  - [ ] ...

### [ ] T2 — <título> (~10 min)

- **Capa:** ...
- **Archivos:** `src/...`
- **Depende de:** T1
- **Qué hacer:**
  1. ...
- **Criterios de aceptación:**
  - [ ] ...

## Criterios de aceptación globales

**Específicos:**

- [ ] ...

**Fijos:**

- [ ] Todas las tareas y sus criterios están marcados como cumplidos.
- [ ] `./mvnw verify` termina en `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` arranca y `GET /actuator/health` responde `{"status":"UP"}`.
- [ ] Se respeta la arquitectura de la fase (capas: cada capa solo usa la inferior / hexagonal: las dependencias apuntan al hexágono), verificado por ArchUnit.
- [ ] El dominio no depende de Spring, `jakarta` ni `org.immutables`, y no usa `@Data`, `@Setter`, `@Builder` ni `@AllArgsConstructor` (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` sin errores).
- [ ] Los tests nuevos siguen `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` en español).
- [ ] No se añaden dependencias fuera de las previstas (o se justifica en un ADR).
- [ ] Si se toca la API: `docs/api/openapi.yaml` refleja los cambios y `npx @redocly/cli lint docs/api/openapi.yaml` no da errores (si no, "no aplica").
- [ ] Si aparece un término de negocio nuevo, está en `docs/dominio/glosario.md`.

## Riesgos y preguntas abiertas

- ...

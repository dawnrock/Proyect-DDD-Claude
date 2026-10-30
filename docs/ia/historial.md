# Historial del proyecto

> Anexo de [`estado-del-proyecto.md`](estado-del-proyecto.md). **No se lee al empezar cada sesión**: cada PR que cierra una issue añade aquí su fila.

| Fecha | PR | Issue | Cambio de estado |
| ----- | -- | ----- | ---------------- |
| 2026-10-23 | #1 | — | Arquitectura de IA en `Monolithic` |
| 2026-10-23 | #24 | #2 | Lombok e Immutables en el build |
| 2026-10-23 | #32 | #31 (parcial) | Flujo de equipo back/web y backlog reorganizado |
| 2026-10-23 | #33, #34 | #3 | Glosario, capas, ADR 0004 y diagrama hexagonal |
| 2026-10-23 | #35 | #4 | Repositorio en memoria |
| 2026-10-23 | #36 | #8 | Base REST + web: errores a HTTP, OpenAPI, Thymeleaf y página 404 |
| 2026-10-23 | #37 | — | Estado del proyecto entre sesiones, exigido en cada PR |
| 2026-10-23 | #38 | — | Documentación en inglés; plan de la #5 aprobado |
| 2026-10-23 | #39 | #5 | Abrir, consultar y listar cuentas (REST + web), primera issue en equipo; cierra también la #31 (flujo en equipo) |
| 2026-10-23 | #40 | — | Interruptor `TELEGRAM_DISABLED` para silenciar los avisos |
| 2026-10-24 | #41 | #6 | Ingresos y retiradas con detalle de la cuenta (REST + web); cierra el pendiente de sinónimos heredados |
| 2026-10-25 | #43 | #7 | Transferencias entre cuentas (REST + web): primer servicio de dominio `MoneyTransferService` (R17-R19) |
| 2026-10-25 | #44 | — | Quita la regla deny `Write(./.claude/hooks/**)`, que nunca se aplicaba |
| 2026-10-25 | #45 | #11 | Datos de demostración con el perfil `demo` y builder de cuentas de test `TestAccounts` |
| 2026-10-25 | #46 | — | Quita del estado el pendiente resuelto de las ramas de los teammates |
| 2026-10-25 | #47 | #12 | `LayeredArchitectureTests`: D1-D5 de la fase 1, sin setters públicos en el dominio y controladores sin repositorio |
| 2026-10-25 | #49 | #48 | Rediseño oscuro y minimalista de la web, `WebFormat` e importes en formato español |
| 2026-10-25 | #51 | #50 | Login con roles admin y usuario (Spring Security, ADR 0008), propiedad de cuentas y transferencia por IBAN |
| 2026-10-26 | #52 | — | Análisis funcional y técnico (`docs/analysis/`, en castellano como excepción) y `check-domain` de `@EqualsAndHashCode` |
| 2026-10-26 | #53, #55 | — | ADR 0009: el dominio no usa Lombok; `check-domain` rechaza cualquier `lombok`; referencias a ArchUnit corregidas |
| 2026-10-26 | #56 | #54 | Reglas de ArchUnit de los ADR 0004 y 0008 y `DomainIsolationTests` sin el paquete `bootstrap` |
| 2026-10-30 | #58 | #57 | Graphify solo para preguntas de dependencias (ADR 0010) y estado del proyecto dividido en estado corto + anexos |
| 2026-10-30 | #60 | #13 | Cierre de la fase 1: `README`, `fase-1-capas.md` y `arquitectura-ia.md` en inglés con el estado final, etiqueta `v1-monolito` y `Hexagonal` como rama por defecto |

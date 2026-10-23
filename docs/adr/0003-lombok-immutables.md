# 0003 · Dónde se usan Lombok e Immutables

- **Estado:** aceptada (las versiones se fijan en la issue "Configurar Lombok e Immutables en el build") · 2026-10-23

## Contexto

Se quiere practicar con Lombok e Immutables, pero el dominio debe proteger sus invariantes y no depender de librerías (`DomainIsolationTests`).

## Decisión

- **Lombok** fuera del dominio para inyección por constructor (`@RequiredArgsConstructor`) y logs (`@Slf4j`); en el dominio solo `@Getter` con accesores fluidos y `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`. `@Builder` solo en builders de test.
- **Immutables** para commands, queries y vistas de `application` y para DTOs de los adaptadores. **Nunca** en `domain..`.
- Los objetos de valor del dominio siguen siendo `record` escritos a mano.
- `scripts/check-domain.mjs` (skill `ddd-building-block`) y la CI comprueban lo que ArchUnit no ve (anotaciones de Lombok con retención `SOURCE`). En la fase 2, ArchUnit prohíbe además `org.immutables` en el dominio.

## Riesgo

Spring Boot 4 usa **Jackson 3**. La integración de Immutables con Jackson se diseñó para Jackson 2: se probará en la issue de la API REST. Si falla, los DTOs REST serán `record` y Immutables se quedará en los commands y vistas de la aplicación. El resultado se anotará aquí.

## Consecuencias

- Menos código repetitivo en servicios, adaptadores y DTOs sin abrir la puerta a setters en los agregados.
- Dos procesadores de anotaciones en el build (`annotationProcessorPaths`).

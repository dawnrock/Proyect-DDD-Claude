# 0003 · Dónde se usan Lombok e Immutables

- **Estado:** aceptada · 2026-10-23

## Contexto

Se quiere practicar con Lombok e Immutables, pero el dominio debe proteger sus invariantes y no depender de librerías (`DomainIsolationTests`).

## Decisión

- **Lombok** fuera del dominio para inyección por constructor (`@RequiredArgsConstructor`) y logs (`@Slf4j`); en el dominio solo `@Getter` con accesores fluidos y `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`. `@Builder` solo en builders de test.
- **Immutables** para commands, queries y vistas de `application` y para DTOs de los adaptadores. **Nunca** en `domain..`.
- Los objetos de valor del dominio siguen siendo `record` escritos a mano.
- `scripts/check-domain.mjs` (skill `ddd-building-block`) y la CI comprueban lo que ArchUnit no ve (anotaciones de Lombok con retención `SOURCE`). En la fase 2, ArchUnit prohíbe además `org.immutables` en el dominio.

## Configuración

Implementada en la issue [#2](https://github.com/dawnrock/Proyect-DDD-Claude/issues/2).

| Pieza | Valor | Dónde |
| ----- | ----- | ----- |
| Lombok | 1.18.46, versión gestionada por Spring Boot 4.1.1; dependencia `optional` y excluida del JAR ejecutable | [`pom.xml`](../../pom.xml) |
| Immutables | 2.12.3: `org.immutables:value-annotations` (`provided`) para las anotaciones y `org.immutables:value` solo como procesador | [`pom.xml`](../../pom.xml) |
| Procesadores | `maven-compiler-plugin` → `annotationProcessorPaths` con Lombok e Immutables. Al declararlos, Maven deja de buscar procesadores en el classpath, así que hay que listar los dos. Se aplican a `compile` y a `testCompile` | [`pom.xml`](../../pom.xml) |
| Accesores fluidos | `lombok.accessors.fluent = true` (`name()` en lugar de `getName()`), `config.stopBubbling = true` y `lombok.addLombokGeneratedAnnotation = true` | [`lombok.config`](../../lombok.config) |
| Estilo de Immutables | Meta-anotación `@ApplicationValue`: el tipo abstracto lleva el sufijo `Def` y el generado toma el nombre sin él (`OpenAccountCommandDef` → `OpenAccountCommand`), con visibilidad pública y sin métodos `with*` | [`ApplicationValue.java`](../../src/main/java/com/pronoide/ddd/application/ApplicationValue.java) |

Los tests `LombokSetupTests` e `ImmutablesSetupTests` (en `src/test/.../build`) demuestran que los dos procesadores generan código.

**IDE:** para que el IDE reconozca el código generado hay que activar el procesamiento de anotaciones (IntelliJ: *Enable annotation processing*; Eclipse/STS: instalar el agente de Lombok y activar *annotation processing* en el proyecto). `./mvnw` no lo necesita.

## Riesgo

Spring Boot 4 usa **Jackson 3**. La integración de Immutables con Jackson se diseñó para Jackson 2: se probará en la issue de la API REST. Si falla, los DTOs REST serán `record` y Immutables se quedará en los commands y vistas de la aplicación. El resultado se anotará aquí.

## Consecuencias

- Menos código repetitivo en servicios, adaptadores y DTOs sin abrir la puerta a setters en los agregados.
- Dos procesadores de anotaciones en el build (`annotationProcessorPaths`).

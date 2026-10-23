# Plan: Configurar Lombok e Immutables en el build

- **Issue:** [#2](https://github.com/dawnrock/Proyect-DDD-Claude/issues/2)
- **Tipo:** feature (build)
- **Fase:** 1 · Monolito en capas
- **Rama base:** `Monolithic`
- **Fecha:** 2026-10-23
- **Estado:** en curso
- **Estimación total:** ~35 min (4 tareas)

## Objetivo

Dejar el build preparado para usar Lombok e Immutables según la tabla "dónde sí y dónde no" de `CLAUDE.md`, sin tocar todavía ninguna clase de producción del dominio. Al terminar, los dos procesadores de anotaciones generan código en `compile` y `test-compile`, y hay un test por librería que lo demuestra.

### Fuera de alcance

- Usar Lombok o Immutables en clases existentes (`domain.model`) o crear commands reales de la aplicación: llegan en las issues #5 y siguientes.
- Comprobar la integración de Immutables con Jackson 3: se hace en la issue #8 (API REST), como indica el ADR 0003.

## Contexto

- `pom.xml` hereda de `spring-boot-starter-parent` 4.1.1, que gestiona **Lombok 1.18.46** y `maven-compiler-plugin` 3.15.0. Hoy no hay configuración propia del compilador ni procesadores de anotaciones.
- En cuanto se declara `annotationProcessorPaths`, Maven deja de descubrir procesadores en el classpath. Por eso hay que listar **los dos** (Lombok e Immutables), y se aplican tanto a `compile` como a `testCompile`.
- Immutables: **2.12.3** (última release en Maven Central, publicada el 2026-10-11). Se separan las anotaciones (`org.immutables:value-annotations`, `provided`) del procesador (`org.immutables:value`, solo en `annotationProcessorPaths`), para que el procesador no acabe en el classpath de ejecución.
- Lombok es `optional` y se excluye del JAR ejecutable en `spring-boot-maven-plugin`, que es la configuración estándar de Spring Initializr.
- El estilo de Immutables se define como meta-anotación en `application`, porque es allí donde vivirán los commands y vistas (ver `.claude/skills/ddd-building-block/references/lombok-immutables.md`). Al no estar en `domain..`, `DomainIsolationTests` no se ve afectado.
- Los tipos de ejemplo de los tests viven en `src/test`. ArchUnit no los analiza, porque usa `ImportOption.DoNotIncludeTests`.

| Capa / paquete | Archivo | Cambio |
| -------------- | ------- | ------ |
| build | `pom.xml` | Dependencias de Lombok e Immutables, `annotationProcessorPaths` y exclusión de Lombok del JAR |
| build | `lombok.config` | Nuevo: accesores fluidos y configuración común |
| `application` | `src/main/java/com/pronoide/ddd/application/ApplicationValue.java` | Nueva meta-anotación con `@Value.Style` |
| tests | `src/test/java/com/pronoide/ddd/build/LombokSetupTests.java` | Nuevo |
| tests | `src/test/java/com/pronoide/ddd/build/ImmutablesSetupTests.java` | Nuevo |
| docs | `docs/adr/0003-lombok-immutables.md` | Versiones elegidas y configuración |

## Tareas

### [x] T1 — Lombok en el build con accesores fluidos (~10 min)

- **Capa:** build
- **Archivos:** `pom.xml`, `lombok.config`, `src/test/java/com/pronoide/ddd/build/LombokSetupTests.java`
- **Depende de:** —
- **Qué hacer:**
  1. **Rojo:** escribir `LombokSetupTests` con una clase de ejemplo anidada (`@Getter @RequiredArgsConstructor`, campo `name`) y un test que llama a `sample.name()`. Debe fallar al compilar porque Lombok aún no existe.
  2. Añadir `org.projectlombok:lombok` (`optional`, sin versión: la gestiona Spring Boot).
  3. Configurar `maven-compiler-plugin` con `annotationProcessorPaths` → `lombok` (`${lombok.version}`).
  4. Excluir `lombok` del JAR en `spring-boot-maven-plugin`.
  5. Crear `lombok.config` con `config.stopBubbling = true`, `lombok.accessors.fluent = true` y `lombok.addLombokGeneratedAnnotation = true`.
- **Criterios de aceptación:**
  - [x] `./mvnw test -Dtest=LombokSetupTests` pasa, y el accesor generado es `name()`, no `getName()`.
  - [x] `./mvnw verify` en verde, con las 22 pruebas anteriores más la nueva.

### [x] T2 — Procesador de Immutables en el build (~5 min)

- **Capa:** build
- **Archivos:** `pom.xml`
- **Depende de:** T1
- **Qué hacer:**
  1. Añadir la propiedad `immutables.version` = `2.12.3`.
  2. Dependencia `org.immutables:value-annotations` (`provided`).
  3. Añadir `org.immutables:value` (`${immutables.version}`) a `annotationProcessorPaths`, junto a Lombok.
- **Criterios de aceptación:**
  - [x] `./mvnw verify` en verde: Lombok sigue funcionando con los dos procesadores declarados.
  - [x] `org.immutables:value` no aparece en `./mvnw dependency:tree` (solo `value-annotations`, con scope `provided`).

### [x] T3 — Estilo común `@ApplicationValue` y test de Immutables (~15 min)

- **Capa:** application
- **Archivos:** `src/main/java/com/pronoide/ddd/application/ApplicationValue.java`, `src/test/java/com/pronoide/ddd/build/ImmutablesSetupTests.java`
- **Depende de:** T2
- **Qué hacer:**
  1. **Rojo:** escribir `ImmutablesSetupTests` con una interfaz de ejemplo `@ApplicationValue @Value.Immutable interface SampleCommandDef { String holderName(); BigDecimal amount(); }` y tests que usan el `SampleCommand` generado: el builder construye la instancia, dos instancias con los mismos valores son iguales, y falta un atributo obligatorio → `IllegalStateException`.
  2. Crear la meta-anotación `ApplicationValue` (`@Target({PACKAGE, TYPE})`, `@Retention(CLASS)`) con `@Value.Style(typeAbstract = "*Def", typeImmutable = "*", visibility = PUBLIC, defaults = @Value.Immutable(copy = false))`, documentando en su Javadoc que no se usa en `domain..`.
- **Criterios de aceptación:**
  - [x] `./mvnw test -Dtest=ImmutablesSetupTests` pasa (con `@Nested` o tests separados para builder, igualdad y atributo obligatorio).
  - [x] `DomainIsolationTests` pasa y `node .claude/skills/ddd-building-block/scripts/check-domain.mjs` no da errores.

### [ ] T4 — Documentar versiones y configuración en el ADR 0003 (~5 min)

- **Capa:** docs
- **Archivos:** `docs/adr/0003-lombok-immutables.md`
- **Depende de:** T3
- **Qué hacer:**
  1. Añadir una sección "Configuración" con las versiones (Lombok 1.18.46 gestionado por Spring Boot 4.1.1, Immutables 2.12.3), el porqué de `annotationProcessorPaths` con los dos procesadores, `lombok.config` y la meta-anotación `@ApplicationValue`.
  2. Cambiar el estado a "aceptada" (sin "versiones pendientes").
- **Criterios de aceptación:**
  - [ ] El ADR enlaza `pom.xml`, `lombok.config` y `ApplicationValue.java`.
  - [ ] `./mvnw verify` en verde.

## Criterios de aceptación globales

**Específicos:**

- [ ] Una clase con `@Getter` genera accesores fluidos (`name()`).
- [ ] Un `@Value.Immutable` con `@ApplicationValue` genera su builder con el nombre sin el sufijo `Def`.
- [ ] Lombok no se incluye en el JAR ejecutable (`target/ddd-lab-0.0.1-SNAPSHOT.jar` sin `BOOT-INF/lib/lombok-*.jar`).
- [ ] `docs/adr/0003-lombok-immutables.md` recoge las versiones elegidas.

**Fijos:**

- [ ] Todas las tareas y sus criterios están marcados como cumplidos.
- [ ] `./mvnw verify` termina en `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` arranca y `GET /actuator/health` responde `{"status":"UP"}`.
- [ ] Se respeta la arquitectura de la fase (capas: cada capa solo usa la inferior), verificado por ArchUnit. Las reglas de capas llegan en la #12; hoy aplica `DomainIsolationTests`.
- [ ] El dominio no depende de Spring, `jakarta` ni `org.immutables`, y no usa `@Data`, `@Setter`, `@Builder` ni `@AllArgsConstructor` (`node .claude/skills/ddd-building-block/scripts/check-domain.mjs` sin errores).
- [ ] Los tests nuevos siguen `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` en español).
- [ ] No se añaden dependencias fuera de las previstas (Lombok e Immutables están previstas en el ADR 0003).
- [ ] Si se toca la API: no aplica.
- [ ] Si aparece un término de negocio nuevo, está en `docs/dominio/glosario.md`: no aplica.

## Riesgos y preguntas abiertas

- **Immutables 2.12.3 se publicó hace 12 días.** Si diera algún problema con JDK 21 o junto a Lombok, se baja a la 2.12.2 y se anota en el ADR.
- **Los IDE necesitan el procesador de anotaciones activado** (Eclipse/STS con el plugin de Lombok; IntelliJ con "Enable annotation processing"). Afecta solo al IDE, no a `./mvnw`. Se menciona en el ADR.

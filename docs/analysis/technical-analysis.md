# Análisis técnico

> **Excepción de idioma:** este documento está en castellano a petición del usuario, como excepción a la convención de escribir la documentación en inglés. No se traduce.

> Fotografía de la rama `Monolithic` (fase 1, monolito en capas) después de la PR #51. Para cada tecnología: qué es, dónde se usa y **por qué** está en el proyecto. Las decisiones que tienen su propio registro enlazan a su ADR.

## 1. Resumen de tecnologías

| Área | Tecnología | Versión | Ámbito |
| ---- | ---------- | ------- | ------ |
| Lenguaje | Java | 21 | — |
| Framework | Spring Boot (padre `spring-boot-starter-parent`) | 4.1.1 | — |
| Web | Spring MVC (`spring-boot-starter-webmvc`) + Thymeleaf | gestionada por Boot | compile |
| JSON | Jackson 3 (`tools.jackson.databind`) | gestionada por Boot | compile |
| Validación | Bean Validation (`spring-boot-starter-validation`) | gestionada por Boot | compile |
| Seguridad | Spring Security (`spring-boot-starter-security`) | gestionada por Boot | compile |
| Operación | Spring Boot Actuator | gestionada por Boot | compile |
| Datos | Spring Data JPA + H2 (en memoria) + consola H2 | gestionada por Boot | compile / runtime |
| Código repetitivo | Lombok | 1.18.x (gestionada por Boot) | optional, procesador de anotaciones |
| Tipos de valor | Immutables | 2.12.3 | provided, procesador de anotaciones |
| Tests | JUnit 5, test slices de Spring Boot, Mockito, Spring Security Test | gestionada por Boot | test |
| Tests de arquitectura | ArchUnit (`archunit-junit5`) | 1.5.0 | test |
| Desarrollo | Spring Boot DevTools | gestionada por Boot | runtime, optional |
| Build | Maven + Maven Wrapper (`mvnw`) | — | — |
| Contrato de la API | OpenAPI 3 (`docs/api/openapi.yaml`) + lint con Redocly CLI | 2.71.0 | CI |
| CI | GitHub Actions (`ci.yml`) + scripts de Node | Node 22 | CI |
| Herramientas de IA | Claude Code (skills, agentes, hooks, workflows de GitHub) | — | proceso de desarrollo |

Añadir una dependencia distinta de Lombok, Immutables, Thymeleaf, Mockito y ArchUnit exige un ADR (`CLAUDE.md`); por eso Spring Security tiene el [ADR 0008](../adr/0008-spring-security.md).

## 2. Arquitectura (fase 1)

```
presentation ── web (Thymeleaf)   ┐   ← no se conocen entre sí (D5)
             └─ rest (API JSON)   ┘
      │  commands / vistas + solo DomainException (D4)
      ▼
application ── CheckingAccountService, *CommandDef, vistas *Def, Caller
      │                     │
      ▼                     ▼
   domain  ◄──────────  persistence (repositorios en memoria)
 (model, service)       solo conoce domain
 solo conoce el JDK (D1)
```

- Clases concretas, sin interfaces de puerto en la fase 1 ([ADR 0004](../adr/0004-arquitectura-en-capas.md)); la fase 2 lo convierte en puertos y adaptadores en la rama `Hexagonal` ([ADR 0002](../adr/0002-ramas-por-fase.md)).
- Cada regla del diagrama es un test ejecutable de ArchUnit (sección 5).

## 3. Lenguaje y framework

### Java 21

- Los **records** modelan los objetos de valor (`MonetaryAmount`, `AccountNumber`) y las peticiones REST: inmutables por construcción, igualdad por valor sin escribirla, y el constructor compacto es el sitio natural para validar y normalizar.
- **Pattern matching en `instanceof`** en los `equals` escritos a mano de las entidades.
- El dominio solo usa el JDK (`BigDecimal`, `Currency`, `UUID`, `Instant`), lo que lo mantiene independiente del framework.

### Spring Boot 4.1

Aporta la inyección de dependencias, el Tomcat embebido, la autoconfiguración y los test slices. Se usa **solo alrededor** del dominio: los servicios de aplicación son `@Service`, los repositorios `@Repository` y los controladores `@Controller`/`@RestController`. El dominio no tiene ninguna anotación de Spring (lo vigila ArchUnit).

### Spring MVC + Thymeleaf ([ADR 0005](../adr/0005-thymeleaf.md))

Plantillas renderizadas en el servidor (`templates/accounts/*.html`, `login.html`, `error/*.html`) con el enlace de formularios y la validación de Spring. **Por qué:** la mejor integración con Spring y Java, sin un proyecto de frontend aparte ni Node en el build, y en la fase 2 la web pasa a ser un adaptador de entrada más. El ADR descartó SPA, JSP y Vaadin.

### Jackson 3

Spring Boot 4.1 trae Jackson 3 (`tools.jackson.databind`). Los DTOs de respuesta REST son tipos de Immutables anotados con `@JsonSerialize(as = …)` del paquete de Jackson 3; `JacksonImmutablesTests` demuestra la ida y vuelta a JSON ([ADR 0003](../adr/0003-lombok-immutables.md), "Riesgo resuelto").

### Bean Validation

Las peticiones REST (`OpenAccountRequest`, `MovementRequest`, `TransferRequest`) son `record` con restricciones, y los formularios web aplican las mismas. **Por qué:** los errores de entrada se detectan en el borde y se devuelven como **400** con un mensaje por campo, antes de llegar al dominio. Los commands de aplicación vuelven a validar (`@Value.Check`), así que el `IllegalArgumentException` del dominio es solo una red de seguridad.

### Spring Security ([ADR 0008](../adr/0008-spring-security.md))

- Dos `SecurityFilterChain`: `WebSecurityConfig` (login con formulario, sesión y CSRF) y `ApiSecurityConfig` (HTTP Basic, sin sesión y sin CSRF en `/api/**`).
- Usuarios en memoria con hash BCrypt (usuarios de demostración en el perfil `demo`; si no, un administrador por variables de entorno).
- Spring comprueba los **roles en las URL**; la **propiedad de la cuenta** es una regla de aplicación que comprueba `CheckingAccountService` con un `Caller` sencillo, no con `@PreAuthorize`, para que se pueda probar sin Spring y pasar tal cual a la fase 2.
- **Por qué una librería y no un filtro propio:** el hash de contraseñas, el CSRF, la fijación de sesión y HTTP Basic son fáciles de hacer mal.

### Actuator

Expone `/actuator/health` y `/actuator/info`. El estado de salud es público, pero los detalles solo se muestran con sesión (`show-details=when-authorized`). **Por qué:** es la comprobación mínima de "¿está arrancada?" que usa `CLAUDE.md` y que usaría cualquier despliegue futuro.

### Spring Data JPA + H2

H2 corre en memoria (`jdbc:h2:mem:ddd`) con su consola en `/h2-console` (solo administradores). **Estado actual:** los repositorios son mapas en memoria escritos a mano (`InMemoryCheckingAccountRepository`, `InMemoryAccountOwnershipRepository`), así que JPA y H2 están configurados pero todavía no guardan cuentas. **Por qué están:** vienen del punto de partida del curso y dejan preparada la persistencia real para la fase 2 sin tocar el build; el proyecto de práctica permite expresamente la persistencia simulada.

El repositorio en memoria se comporta como uno real donde importa: número de cuenta único (como un índice único), `findAll` ordenado, listas no modificables, `ConcurrentHashMap` y un `save` `synchronized`.

### DevTools

Reinicio automático durante `spring-boot:run`. Solo en runtime y opcional, así que nunca llega al JAR empaquetado.

## 4. Generación de código: Lombok e Immutables ([ADR 0003](../adr/0003-lombok-immutables.md))

El proyecto quiere practicar con las dos librerías **sin debilitar el dominio**. La regla es "código generado fuera del dominio, código escrito a mano dentro".

### Lombok

| Dónde | Anotación | Por qué |
| ----- | --------- | ------- |
| `application`, `presentation` (servicios, controladores, `DemoDataLoader`) | `@RequiredArgsConstructor` | Inyección por constructor de los campos `final` sin escribir el constructor: dependencias inmutables y fáciles de probar. |
| `DemoDataLoader`, `RestExceptionHandler` | `@Slf4j` | Campo de log sin código repetitivo. |
| Objetos de formulario web (`OpenAccountForm`, `MovementForm`, `TransferForm`) | `@Getter`, `@Setter` y `@Accessors(fluent = false)` | Spring MVC y Thymeleaf enlazan los formularios con getters y setters al estilo JavaBean (`getAmount()`/`setAmount()`), así que aquí se desactivan los accesores fluidos del `lombok.config`. |
| Tests (`support/TestAccounts`) | `@Builder` | Builder de datos de prueba (`TestAccounts.anAccount()...open()`), el único uso permitido de `@Builder`. |
| `domain..` | ninguna ([ADR 0009](../adr/0009-domain-without-lombok.md)) | Cada método generado lleva `@lombok.Generated`, que queda en el bytecode y rompe la regla D1; además, setters, constructores públicos o un `@Getter` sobre colecciones permitirían saltarse las factorías y las invariantes. Los accesores y los `equals` del dominio se escriben a mano. |

Configuración (`lombok.config`): `lombok.accessors.fluent = true` da accesores al estilo record (`id()` en lugar de `getId()`), como pide la convención del proyecto; `addLombokGeneratedAnnotation` permite que las herramientas de cobertura ignoren el código generado. Lombok es `optional` y se excluye del JAR ejecutable porque solo hace falta al compilar.

### Immutables

Se usa para los **commands y las vistas de la capa de aplicación** (`OpenAccountCommandDef`, `DepositCommandDef`, `WithdrawCommandDef`, `TransferCommandDef`, `AccountSummaryDef`, `AccountDetailsDef`, `TransactionSummaryDef`, `TransferSummaryDef`, `CallerDef`) y para los **DTOs de respuesta REST** (`*ResponseDef`).

- La meta-anotación común `@ApplicationValue` fija el estilo de nombres: el tipo abstracto termina en `Def` y la clase generada toma el nombre sin él (`OpenAccountCommandDef` → `OpenAccountCommand`), con builder y sin métodos `with*`.
- `@Value.Default` da valores por defecto (divisa `EUR`) y `@Value.Check` valida al construir, así que nunca puede existir un command no válido.

**Por qué:** estos objetos cruzan las fronteras entre capas y deben ser inmutables, sin nulos y fáciles de construir; Immutables genera todo eso (builders, `equals`/`hashCode`, soporte de Jackson) a partir de una interfaz.

**Por qué no en el dominio:** el código generado deja referencias a `org.immutables` en el bytecode, lo que rompería la regla "el dominio solo depende del JDK". Los objetos de valor del dominio son `record` normales.

### Cómo los conecta el build

Los dos procesadores se declaran en `maven-compiler-plugin` → `annotationProcessorPaths` (al declarar uno, Maven deja de buscarlos en el classpath, así que hay que listar los dos). `LombokSetupTests` e `ImmutablesSetupTests` demuestran que cada procesador genera código de verdad.

## 5. ArchUnit: la arquitectura como tests

ArchUnit lee el bytecode compilado y hace fallar el build cuando se incumple una regla de dependencias. **Por qué:** las reglas de las capas están escritas en `docs/arquitectura/fase-1-capas.md`, pero la documentación no impide un import incorrecto; un test que falla en `./mvnw verify` sí, tanto a las personas como a los agentes de IA.

`LayeredArchitectureTests`:

| Regla | Comprueba |
| ----- | --------- |
| `las_capas_solo_dependen_hacia_abajo` | presentation → application → domain/persistence; nadie accede a presentation (D2, D3) |
| `el_dominio_solo_depende_del_jdk` | el dominio solo depende de `java..` y de sí mismo: ni Spring, ni `jakarta`, ni `org.immutables` (D1) |
| `la_presentacion_solo_conoce_las_excepciones_del_dominio` | la presentación trabaja con commands y vistas y del dominio solo ve las subclases de `DomainException` (D4) |
| `la_web_y_rest_no_se_conocen` | `presentation.web` y `presentation.rest` son porciones independientes (D5) |
| `el_dominio_no_tiene_setters_publicos` | el dominio solo cambia mediante métodos de negocio |
| `los_controladores_no_acceden_al_repositorio` | los controladores pasan por los casos de uso |
| `toda_clase_pertenece_a_una_capa` | ninguna clase vive fuera de las cuatro capas (salvo la clase de arranque) |

`DomainIsolationTests`: el dominio no depende de Spring ni de `jakarta`, y todos los campos no estáticos del dominio son `private`.

**Lo que ArchUnit no ve:** las anotaciones de Lombok tienen retención `SOURCE` y desaparecen antes del bytecode. Ese hueco lo cubre `node .claude/skills/ddd-building-block/scripts/check-domain.mjs`, que revisa el código fuente del dominio en busca de cualquier uso de Lombok, imports de `org.immutables`/Spring/`jakarta` y setters públicos. Se ejecuta en la CI.

En la fase 2, `HexagonalArchitectureTests` sustituye las reglas de capas por reglas de puertos y adaptadores.

## 6. Estrategia de tests

| Nivel | Herramientas | Ejemplos |
| ----- | ------------ | -------- |
| Tests unitarios del dominio | JUnit 5, sin Spring | `CheckingAccountTests`, `MonetaryAmountTests`, `MoneyTransferServiceTests`: cada regla de negocio R1-R19 tiene un test con nombre |
| Aplicación | JUnit 5 + Mockito / repositorios en memoria reales | `CheckingAccountServiceTests`, `CheckingAccountServiceOwnershipTests`, `*CommandTests` |
| Persistencia | JUnit 5 | `InMemoryCheckingAccountRepositoryTests` |
| Slices web / REST | `@WebMvcTest`, `MockMvc`, Mockito, `@WithMockUser`, CSRF | `AccountControllerTests`, `AccountWebControllerTests`, `WebSecurityTests` |
| Contexto completo | `@SpringBootTest` | `DddLabApplicationTests`, `DemoProfileTests` |
| Arquitectura | ArchUnit | `LayeredArchitectureTests`, `DomainIsolationTests` |
| Configuración del build | JUnit 5 | `LombokSetupTests`, `ImmutablesSetupTests`, `JacksonImmutablesTests` |

Convenciones: nombres de método y `@DisplayName` en español que describen la regla; builders de datos de prueba en `support/TestAccounts`. **Mockito** llega con los starters de test de Spring Boot y aísla los controladores del servicio de aplicación. Se trabaja con TDD estricto (`/implement-plan`).

## 7. Contrato de la API y gestión de errores

- `docs/api/openapi.yaml` es el contrato REST; cualquier cambio de la API lo actualiza y la CI lo valida con **Redocly CLI**.
- `RestExceptionHandler` traduce las excepciones a `ProblemDetail` (RFC 9457): `DomainException` → 422, `NotFoundException` → 404, validación / `IllegalArgumentException` → 400, `OperationNotAllowedException` (por ejemplo, `ForbiddenOperationException`) → 403.
- `WebExceptionHandler` es un `@ControllerAdvice` acotado con `basePackageClasses` a `presentation.web`, para que nunca capture errores de la API.
- Los contratos back ↔ web de cada funcionalidad están en `docs/contracts/` ([ADR 0007](../adr/0007-equipo-back-web-contrato-primero.md)).

## 8. Build, CI y reglas del repositorio

- **Maven Wrapper** (`./mvnw verify`, `mvnw.cmd` en Windows) fija la versión de Maven: no hace falta instalarlo. `verify` compila y ejecuta JUnit + ArchUnit; es la definición de "terminado" del proyecto.
- `spring-boot:run` arranca con el perfil `demo` (propiedad `spring-boot.run.profiles` del `pom.xml`).
- **GitHub Actions `ci.yml`** en los push y las PR de `Monolithic`/`Hexagonal`:
  1. `scripts/check-project-status.mjs`: una PR que cierra una issue debe actualizar `docs/ia/estado-del-proyecto.md`.
  2. `check-domain.mjs`: reglas del dominio que ArchUnit no ve.
  3. Lint del contrato OpenAPI con Redocly.
  4. `./mvnw -B verify`.
  5. Aviso por Telegram si falla.
- `claude-code-review.yml` y `claude.yml` añaden una revisión automática de Claude en las PR.
- Protección de ramas: `master` congelada y `Monolithic` solo mediante PR con `verify` en verde.

## 9. Herramientas de IA (Claude Code)

Parte del objetivo del proyecto es practicar un flujo de trabajo asistido por IA ([`arquitectura-ia.md`](../ia/arquitectura-ia.md), [ADR 0001](../adr/0001-arquitectura-ia-claude-code.md)):

| Pieza | Para qué |
| ----- | -------- |
| Skills `plan-feature`, `implement-plan`, `implement-plan-team` | Issue de GitHub → plan en `docs/plans/` → implementación con TDD en un worktree → PR |
| Skills `ddd-building-block`, `hexagonal-adapter` | Plantillas y reglas para los bloques del dominio y los adaptadores de la fase 2 |
| Agentes `backend-dev`, `web-dev` | Implementación en paralelo de back y web contra un contrato congelado |
| Agentes `revisor-ddd`, `buscador-referencias` | Revisión de DDD y arquitectura antes de una PR; búsquedas amplias en el código |
| Hooks | Bloquear la edición del JSON del backlog generado, limpiar worktrees fusionados y avisos por Telegram |
| `CLAUDE.md` + `estado-del-proyecto.md` | Reglas persistentes y estado actual para cada sesión |

**Por qué importan aquí las comprobaciones deterministas (ArchUnit, `check-domain.mjs`, Redocly, CI):** los agentes escriben mucho código, y la arquitectura solo es fiable si la hacen cumplir las herramientas, no la revisión a mano de cada línea.

## 10. Deuda técnica y notas para la fase 2

- JPA y H2 están configurados, pero la persistencia es en memoria; la persistencia real y una transacción alrededor de los dos `save` de una transferencia están previstas para la fase 2.
- `MoneyTransferService` se crea con `new` dentro de `CheckingAccountService`; en la fase 2 pasará a ser un bean en `config`.
- `CheckingAccount.tryWithdraw` registra el movimiento antes de evaluarlo; con cuentas que no fueran en EUR, un cliente de alto patrimonio podría provocar un `CurrencyMismatchException` dejando un movimiento `PENDING`.
- `DomainIsolationTests` sigue prohibiendo un paquete `com.pronoide.ddd.bootstrap..` que no existe (resto inofensivo).
- Falta un test: "el nombre del titular es obligatorio" (`AccountHolder`).

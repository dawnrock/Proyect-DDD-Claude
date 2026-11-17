# Análisis técnico

> **Excepción de idioma:** este documento está en castellano a petición del usuario, como excepción a la convención de escribir la documentación en inglés. No se traduce.

> Fotografía de la rama `Hexagonal` (fase 2, puertos y adaptadores) después del alta de clientes de Mon-A Bank (#103, PR #104). Para cada tecnología: qué es, dónde se usa y **por qué** está en el proyecto. Las decisiones que tienen su propio registro enlazan a su ADR. `Monolithic` tiene su propia versión de este análisis, para la fase 1.

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
| Tareas programadas | `@Scheduled` de Spring (`SchedulingConfiguration`) | gestionada por Boot | compile |
| Datos | Spring Data JPA + H2 (en memoria) + consola H2 | gestionada por Boot | compile / runtime |
| Código repetitivo | Lombok | gestionada por Boot | optional, procesador de anotaciones |
| Tipos de valor | Immutables | 2.12.3 | provided, procesador de anotaciones |
| Tests | JUnit 5, test slices de Spring Boot, Mockito, Spring Security Test | gestionada por Boot | test |
| Tests de arquitectura | ArchUnit (`archunit-junit5`) | 1.5.0 | test |
| Desarrollo | Spring Boot DevTools | gestionada por Boot | runtime, optional |
| Build | Maven + Maven Wrapper (`mvnw`) | — | — |
| Contrato de la API | OpenAPI 3 (`docs/api/openapi.yaml`) + lint con Redocly CLI | 2.71.0 en la CI | CI |
| CI | GitHub Actions (`ci.yml`) + scripts de Node | Node 22 | CI |
| Despliegue | `Dockerfile` + Render (plan gratuito) | — | [guía](../deploy/render.md) |
| Herramientas de IA | Claude Code (skills, agentes, hooks, workflows de GitHub) y Graphify | — | proceso de desarrollo |

Añadir una dependencia distinta de Lombok, Immutables, Thymeleaf, Mockito y ArchUnit exige un ADR (`CLAUDE.md`); por eso Spring Security tiene el [ADR 0008](../adr/0008-spring-security.md).

## 2. Arquitectura (fase 2)

```
   adaptadores de entrada                 hexágono                  adaptadores de salida
┌───────────────────────┐   ┌────────────────────────────────────┐   ┌──────────────────────────┐
│ adapter.in.web        │   │ application                        │   │ adapter.out.persistence  │
│ adapter.in.rest       │──▶│   puertos de entrada:              │   │   cuentas, clientes,     │
│ adapter.in.cli        │   │   CheckingAccountCommands/Queries  │   │   propiedad, numeración, │
│ adapter.in.scheduler  │   │   CheckingAccountService           │◀──│   unidad de trabajo      │
│ adapter.in (soporte:  │   │   puertos de salida de aplicación  │   │ adapter.out.event        │
│  seguridad, alta)     │   │ domain.model (+ puertos de salida) │   │ adapter.out.taxagency    │
└───────────────────────┘   │ domain.service                     │   └──────────────────────────┘
                            └────────────────────────────────────┘
                 config: construye el hexágono y le entrega los adaptadores por sus puertos
```

- **Las dependencias apuntan siempre hacia el hexágono** ([ADR 0006](../adr/0006-hexagonal-architecture.md)). Los adaptadores de entrada solo conocen los puertos de entrada (y, del dominio, `DomainException`); los de salida implementan un puerto y son de paquete, invisibles fuera de su paquete y de `config`.
- **Puertos de salida en el dominio:** `CheckingAccountRepository`, `CustomerRepository`, `AccountNumberSequence`, `IncomeVerifier`, `DomainEventPublisher`.
- **Puertos de salida en `application`:** `AccountOwnershipRepository`, `CustomerOwnershipRepository` y `UnitOfWork`. Están ahí porque la identidad del usuario y el límite de un caso de uso son cosa de la aplicación, no del dominio.
- **El núcleo no tiene anotaciones de Spring** (#20): `config.DomainConfiguration` declara los `@Bean` del servicio, la factoría, los servicios de dominio y los consumidores de eventos.
- Cada regla del diagrama es un test ejecutable de ArchUnit (sección 5). Diseño completo en [`fase-2-hexagonal.md`](../arquitectura/fase-2-hexagonal.md) y comparación con la fase 1 en [`comparativa-capas-hexagonal.md`](../arquitectura/comparativa-capas-hexagonal.md).

## 3. Lenguaje y framework

### Java 21

- Los **records** modelan los objetos de valor (`MonetaryAmount`, `AccountNumber`, `TaxId`, `EmailAddress`), los eventos de dominio y las peticiones REST: inmutables por construcción, igualdad por valor sin escribirla, y el constructor compacto es el sitio natural para validar y normalizar.
- **Pattern matching en `instanceof`** en los `equals` escritos a mano de las entidades.
- El dominio solo usa el JDK (`BigDecimal`, `Currency`, `UUID`, `Instant`), lo que lo mantiene independiente del framework.

### Spring Boot 4.1

Aporta la inyección de dependencias, el Tomcat embebido, la autoconfiguración y los test slices. Se usa **solo en el borde**: controladores (`@Controller`/`@RestController`), adaptadores de salida (`@Component`/`@Repository`) y `config`. El dominio y `application` no llevan anotaciones de Spring; la única excepción es `@EventListener` en `application.listener`, porque los eventos llegan a los consumidores por el mecanismo de eventos de Spring.

### Spring MVC + Thymeleaf ([ADR 0005](../adr/0005-thymeleaf.md))

Plantillas renderizadas en el servidor (`templates/accounts/*.html`, `templates/signup/*.html`, `login.html`, `error/*.html`) con el enlace de formularios y la validación de Spring. **Por qué:** la mejor integración con Spring y Java, sin un proyecto de frontend aparte ni Node en el build. En esta fase la web es un adaptador de entrada más. El alta en tres pasos guarda el formulario en la sesión entre pasos (`@SessionAttributes`).

### Jackson 3

Spring Boot 4.1 trae Jackson 3 (`tools.jackson.databind`). Los DTOs de respuesta REST son tipos de Immutables anotados con `@JsonSerialize(as = …)` del paquete de Jackson 3; `JacksonImmutablesTests` demuestra la ida y vuelta a JSON ([ADR 0003](../adr/0003-lombok-immutables.md)).

### Bean Validation

Las peticiones REST (`OpenAccountRequest`, `SignUpRequest`, `MovementRequest`, `TransferRequest`) son `record` con restricciones, y los formularios web aplican las mismas, compartiendo los patrones (`TAX_ID_PATTERN`, `EMAIL_PATTERN`, `USERNAME_PATTERN`) declarados en los commands. **Por qué:** los errores de entrada se detectan en el borde y se devuelven como **400** con un mensaje por campo, antes de llegar al dominio. Los commands vuelven a validar (`@Value.Check`), así que el `IllegalArgumentException` del dominio es solo una red de seguridad.

### Spring Security ([ADR 0008](../adr/0008-spring-security.md), [ADR 0012](../adr/0012-customer-self-sign-up.md))

- Dos `SecurityFilterChain`: `WebSecurityConfig` (login con formulario, sesión y CSRF) y `ApiSecurityConfig` (HTTP Basic, sin sesión y sin CSRF en `/api/**`). El alta (`/signup/**` y `POST /api/customers`) es pública.
- Usuarios en un `InMemoryUserDetailsManager` con hash BCrypt: los de configuración (perfil `demo` o un administrador por variables de entorno) y los que crea el alta en tiempo de ejecución, a través de `CustomerRegistration`.
- Spring comprueba los **roles en las URL**; la **propiedad de la cuenta** es una regla de aplicación que comprueba `CheckingAccountService` con un `Caller` sencillo, no con `@PreAuthorize` (lo prohíbe la regla `nadie_usa_la_seguridad_por_metodos`).
- **Por qué una librería y no un filtro propio:** el hash de contraseñas, el CSRF, la fijación de sesión y HTTP Basic son fáciles de hacer mal.

### Tareas programadas

`SegmentReviewScheduler` (`adapter.in.scheduler`) llama cada día a las 3:00 a `CheckingAccountCommands.reviewSegments()`; aquí es el reloj quien llama al hexágono. La programación se activa en `config.SchedulingConfiguration`. El reloj de la aplicación es un bean `Clock` que los tests sustituyen por uno que se puede adelantar.

### Actuator

Expone `/actuator/health` y `/actuator/info`. El estado de salud es público, pero los detalles solo se muestran con sesión. **Por qué:** es la comprobación mínima de "¿está arrancada?" que usan `CLAUDE.md` y el despliegue en Render.

### Spring Data JPA + H2

H2 corre en memoria (`jdbc:h2:mem:ddd`) con su consola en `/h2-console` (solo administradores; desactivada en la imagen de Docker). **Estado actual:** todos los adaptadores de persistencia son mapas en memoria escritos a mano, así que JPA y H2 están configurados pero no guardan nada. **Por qué están:** vienen del punto de partida del curso y dejan preparada la persistencia real; gracias a los puertos, cambiarla solo tocaría `adapter.out.persistence`.

Los adaptadores en memoria se comportan como uno real donde importa: número de cuenta único (como un índice único) que se reindexa al cambiar el IBAN, un cliente por documento y un usuario por cliente comprobados de forma atómica, `findAll` ordenado y listas no modificables. `InMemoryUnitOfWork` ejecuta cada caso de uso bajo un cerrojo justo (#78): nadie ve una cuenta a medio cambiar, pero **no deshace cambios**, así que los casos de uso comprueban todo antes de guardar.

### DevTools

Reinicio automático durante `spring-boot:run`. Solo en runtime y opcional, así que nunca llega al JAR empaquetado.

## 4. Generación de código: Lombok e Immutables ([ADR 0003](../adr/0003-lombok-immutables.md), [ADR 0009](../adr/0009-domain-without-lombok.md))

El proyecto quiere practicar con las dos librerías **sin debilitar el dominio**. La regla es "código generado fuera del dominio, código escrito a mano dentro".

### Lombok

| Dónde | Anotación | Por qué |
| ----- | --------- | ------- |
| Adaptadores de entrada y de salida (controladores, `CustomerRegistration`, `DomainModelRunner`, `SegmentReviewScheduler`, `SpringDomainEventPublisher`) | `@RequiredArgsConstructor` | Inyección por constructor de los campos `final` sin escribir el constructor. El servicio de aplicación no la usa: es parte del núcleo y su constructor lo llama `config`. |
| `RestExceptionHandler`, `DomainModelRunner`, `SegmentReviewScheduler`, los consumidores de eventos | `@Slf4j` | Campo de log sin código repetitivo. |
| Formularios web (`OpenAccountForm`, `MovementForm`, `TransferForm`, `SignUpForm`, `AnotherAccountForm`) | `@Getter`, `@Setter` y `@Accessors(fluent = false)` | Spring MVC y Thymeleaf enlazan los formularios con getters y setters al estilo JavaBean, así que aquí se desactivan los accesores fluidos del `lombok.config`. |
| Tests (`support/TestAccounts`) | `@Builder` | Builder de datos de prueba, el único uso permitido de `@Builder`. |
| `domain..` | ninguna | Cada método generado deja `@lombok.Generated` en el bytecode y rompe la regla D1; además, setters o constructores públicos permitirían saltarse las factorías y las invariantes. Los accesores y los `equals` del dominio se escriben a mano. |

Configuración (`lombok.config`): `lombok.accessors.fluent = true` da accesores al estilo record (`id()` en lugar de `getId()`); `addLombokGeneratedAnnotation` permite que las herramientas de cobertura ignoren el código generado. Lombok es `optional` y se excluye del JAR ejecutable.

### Immutables

Se usa para los **commands y las vistas de `application`** (`OpenAccountCommandDef`, `SignUpCommandDef`, `OpenAnotherAccountCommandDef`, `DepositCommandDef`, `WithdrawCommandDef`, `TransferCommandDef`, `AccountSummaryDef`, `AccountDetailsDef`, `TransactionSummaryDef`, `TransferSummaryDef`, `CallerDef`) y para los **DTOs de respuesta REST** (`*ResponseDef`).

- La meta-anotación común `@ApplicationValue` fija el estilo de nombres: el tipo abstracto termina en `Def` y la clase generada toma el nombre sin él (`OpenAccountCommandDef` → `OpenAccountCommand`), con builder y sin métodos `with*`.
- `@Value.Default` da valores por defecto (divisa `EUR`, campos de cliente vacíos) y `@Value.Check` valida al construir, así que nunca puede existir un command no válido.

**Por qué:** estos objetos cruzan las fronteras del hexágono y deben ser inmutables, sin nulos y fáciles de construir. **Por qué no en el dominio:** el código generado deja referencias a `org.immutables` en el bytecode, lo que rompería la regla "el dominio solo depende del JDK".

### Cómo los conecta el build

Los dos procesadores se declaran en `maven-compiler-plugin` → `annotationProcessorPaths` (al declarar uno, Maven deja de buscarlos en el classpath, así que hay que listar los dos). `LombokSetupTests` e `ImmutablesSetupTests` demuestran que cada procesador genera código de verdad.

## 5. ArchUnit: la arquitectura como tests

ArchUnit lee el bytecode compilado y hace fallar el build cuando se incumple una regla de dependencias. **Por qué:** la documentación no impide un import incorrecto; un test que falla en `./mvnw verify` sí, tanto a las personas como a los agentes de IA. Cada regla nueva se valida rompiéndola a propósito (`src/test/CLAUDE.md`).

`HexagonalArchitectureTests` (#22):

| Regla | Comprueba |
| ----- | --------- |
| `las_dependencias_apuntan_hacia_el_hexagono` | `onionArchitecture()`: modelo, servicios de dominio, aplicación y un adaptador por tecnología |
| `los_puertos_de_entrada_son_interfaces` | `CheckingAccountCommands` y `CheckingAccountQueries` son interfaces |
| `los_adaptadores_de_entrada_solo_conocen_los_puertos_de_entrada` | ningún adaptador de entrada depende de `CheckingAccountService` |
| `los_adaptadores_de_entrada_solo_conocen_las_excepciones_del_dominio` | del dominio, los adaptadores de entrada solo ven `DomainException` y sus subclases (D4) |
| `los_adaptadores_de_salida_solo_implementan_puertos_del_hexagono`, `los_adaptadores_de_salida_solo_conocen_application_por_sus_puertos` | un adaptador de salida implementa un puerto y no conoce más de `application` |
| `solo_adapter_out_conoce_los_adaptadores_de_salida`, `nadie_fuera_de_adapter_in_depende_de_adapter_in`, `el_soporte_de_adapter_in_no_conoce_a_los_adaptadores` | aislamiento entre adaptadores y del soporte compartido de `adapter.in` |
| `el_nucleo_no_depende_de_spring`, `solo_los_adaptadores_y_config_dependen_de_spring_web` | el hexágono no conoce Spring (salvo `@EventListener`) ni la web |
| `la_aplicacion_no_depende_de_spring_security`, `nadie_usa_la_seguridad_por_metodos` | las credenciales se quedan en los adaptadores de entrada |
| `el_dominio_solo_depende_del_jdk`, `el_dominio_no_tiene_setters_publicos` | D1 y el dominio solo cambia por métodos de negocio |
| `nadie_depende_de_config`, `toda_clase_pertenece_al_hexagono_o_a_un_adaptador` | `config` solo cablea; ninguna clase queda fuera |

`DomainIsolationTests`: el dominio no depende de Spring ni de `jakarta`, y su estado es privado. `AdapterTestIsolationTests` (sobre los tests): ningún test de un adaptador de entrada levanta el contexto completo (#21).

**Lo que ArchUnit no ve:** las anotaciones de Lombok tienen retención `SOURCE`. Ese hueco lo cubre `node .claude/skills/ddd-building-block/scripts/check-domain.mjs`, que revisa el código fuente del dominio en busca de Lombok, imports de `org.immutables`/Spring/`jakarta` y setters públicos. Se ejecuta en la CI.

## 6. Estrategia de tests

| Nivel | Herramientas | Ejemplos |
| ----- | ------------ | -------- |
| Dominio | JUnit 5, sin Spring ni mocks | `CheckingAccountTests`, `CustomerTests`, `TaxIdTests`, `CheckingAccountFactoryTests`, `SegmentReviewServiceTests`: cada regla R1-R32 tiene su test con nombre (glosario) |
| Aplicación | JUnit 5 con los adaptadores en memoria reales detrás de los puertos | `CheckingAccountServiceTests`, `CheckingAccountServiceSignUpTests`, `CheckingAccountServiceAdminOpeningTests`, `CheckingAccountServiceConcurrencyTests` |
| Contratos de los puertos | clases abstractas `*Contract` que hereda el test de cada adaptador | `CheckingAccountRepositoryContract`, `CustomerRepositoryContract`, `CustomerOwnershipRepositoryContract`, `UnitOfWorkContract` |
| Adaptadores de entrada | `@WebMvcTest`, `MockMvc`, `@MockitoBean` sobre los puertos, `@WithMockUser`, CSRF | `AccountControllerTests`, `SignUpControllerTests`, `AccountWebControllerTests`, `WebSecurityTests` |
| Contexto completo | `@SpringBootTest` | `DddLabApplicationTests` (recorridos de extremo a extremo por la web y la API), `DemoProfileTests` |
| Arquitectura | ArchUnit | `HexagonalArchitectureTests`, `DomainIsolationTests`, `AdapterTestIsolationTests` |
| Configuración del build | JUnit 5 | `LombokSetupTests`, `ImmutablesSetupTests`, `JacksonImmutablesTests` |

Convenciones (`src/test/CLAUDE.md`): nombres de método en `snake_case` y `@DisplayName` en español que describen la regla; Mockito solo para simular puertos, nunca clases del dominio. Se trabaja con TDD estricto (`/implement-plan`).

## 7. Contrato de la API y gestión de errores

- `docs/api/openapi.yaml` es el contrato REST; cualquier cambio de la API lo actualiza y la CI lo valida con **Redocly CLI**.
- `RestExceptionHandler` traduce las excepciones a `ProblemDetail` (RFC 9457): `DomainException` → 422, `UsernameTakenException` → 422, `NotFoundException` → 404, validación / `IllegalArgumentException` → 400, `OperationNotAllowedException` (`ForbiddenOperationException`, `NotACustomerException`) → 403.
- `WebExceptionHandler` es un `@ControllerAdvice` acotado con `basePackageClasses` a `adapter.in.web`, para que nunca capture errores de la API.
- Los contratos back ↔ web de cada funcionalidad están en `docs/contracts/` ([ADR 0007](../adr/0007-equipo-back-web-contrato-primero.md)).

## 8. Build, CI, despliegue y reglas del repositorio

- **Maven Wrapper** (`./mvnw verify`, `mvnw.cmd` en Windows) fija la versión de Maven. `verify` compila y ejecuta JUnit + ArchUnit; es la definición de "terminado" del proyecto.
- `spring-boot:run` arranca con el perfil `demo` (propiedad `spring-boot.run.profiles` del `pom.xml`).
- **GitHub Actions `ci.yml`** en los push y las PR de `Monolithic`/`Hexagonal`:
  1. `scripts/check-project-status.mjs`: una PR que cierra una issue debe actualizar `docs/ia/estado-del-proyecto.md`.
  2. `check-domain.mjs`: reglas del dominio que ArchUnit no ve.
  3. Lint del contrato OpenAPI con Redocly.
  4. `./mvnw -B verify`.
  5. Aviso por Telegram si falla.
- `claude-code-review.yml` y `claude.yml` añaden una revisión automática de Claude en las PR.
- **Despliegue:** el `Dockerfile` construye la imagen y cada rama se publica como un servicio web en Render ([guía](../deploy/render.md)); la imagen apaga la consola H2 y la contraseña de demostración se sustituye por variables de entorno.
- Protección de ramas: `master` y `Monolithic` congeladas (salvo excepciones que autoriza el usuario); `Hexagonal` es la rama por defecto y solo cambia por PR con `verify` en verde.

## 9. Herramientas de IA (Claude Code)

Parte del objetivo del proyecto es practicar un flujo de trabajo asistido por IA ([`arquitectura-ia.md`](../ia/arquitectura-ia.md), [ADR 0001](../adr/0001-arquitectura-ia-claude-code.md)):

| Pieza | Para qué |
| ----- | -------- |
| Skills `plan-feature`, `implement-plan`, `implement-plan-team` | Issue de GitHub → plan en `docs/plans/` (solo se guardan los 5 más recientes) → implementación con TDD en un worktree → PR |
| Skills `ddd-building-block`, `hexagonal-adapter` | Plantillas y reglas para los bloques del dominio y para los puertos y adaptadores |
| Agentes `backend-dev`, `web-dev` | Implementación en paralelo de back y web contra un contrato congelado |
| Agentes `revisor-ddd`, `buscador-referencias` | Revisión de DDD y arquitectura antes de una PR; búsquedas amplias en el código |
| Graphify ([ADR 0010](../adr/0010-graphify-token-savings.md)) | Grafo local del código para preguntas de dependencias (`affected`, `path`) sin gastar tokens |
| Hooks | Bloquear la edición del JSON del backlog generado, limpiar worktrees fusionados y avisos por Telegram |
| `CLAUDE.md` + `estado-del-proyecto.md` | Reglas persistentes y estado actual para cada sesión |

**Por qué importan aquí las comprobaciones deterministas (ArchUnit, `check-domain.mjs`, Redocly, CI):** los agentes escriben mucho código, y la arquitectura solo es fiable si la hacen cumplir las herramientas, no la revisión a mano de cada línea.

## 10. Deuda técnica

- JPA y H2 están configurados, pero toda la persistencia es en memoria y se pierde al reiniciar (también en Render). `InMemoryUnitOfWork` no deshace cambios: una persistencia real necesitaría transacciones y restricciones únicas para el documento y el usuario.
- Los consumidores de eventos son síncronos y `pullDomainEvents()` vacía la lista antes de publicar: si un consumidor fallara, el error llegaría al usuario y se perderían los eventos restantes. Hará falta un outbox o `@TransactionalEventListener` cuando un consumidor haga algo real.
- Un fallo del puerto `IncomeVerifier` en una revisión aborta el cargo que la provocó y la revisión diaria del resto de cuentas.
- `Transaction.occurredOn` usa `Instant.now()` y la revisión usa el bean `Clock`, así que los tests adelantan el reloj en lugar de fechar los movimientos.
- No hay límite de intentos de acceso ni de altas, y el correo del alta no se verifica (ADR 0008, ADR 0012).

Los avisos vivos de cada issue están en [`pendientes.md`](../ia/pendientes.md).

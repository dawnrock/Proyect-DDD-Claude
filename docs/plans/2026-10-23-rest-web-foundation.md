# Plan: Base de la API REST y de la web: errores centralizados, plantilla y OpenAPI

- **Issue:** [#8](https://github.com/dawnrock/Proyect-DDD-Claude/issues/8)
- **Tipo:** feature
- **Fase:** 1 · Monolito en capas
- **Rama base:** `Monolithic`
- **Fecha:** 2026-10-23
- **Estado:** en curso
- **Estimación total:** ~65 min (7 tareas)

## Objetivo

Dejar lista la infraestructura común que usarán las funcionalidades verticales (#5, #6 y #7):

- traducción de errores a HTTP desde un único punto;
- plantilla base y página 404 de la web;
- `openapi.yaml` inicial validado en CI;
- respuesta al riesgo del ADR 0003 (Immutables con Jackson 3).

Con esto, los contratos de la #5 en adelante ya parten de unos errores y unos tipos de DTO decididos.

### Fuera de alcance

- Endpoints y pantallas de negocio (llegan con #5, #6 y #7).
- `AccountNotFoundException`: se crea en la #5, heredando de la base `NotFoundException` que añade esta issue.

## Contexto

- **No se implementa en equipo**, aunque la issue tenga `lado:back` y `lado:web`. Las dos partes son independientes: no hay servicio, commands ni vistas que acordar, así que la tarea T0 · Contrato no aporta nada. Se usa `/implement-plan`, y cada tarea indica su `Lado` para que quede claro qué toca.
- **Traducción de errores** (`docs/arquitectura/fase-1-capas.md`, sección "Traducción de errores"):
  - `DomainException` → 422.
  - `NotFoundException` (aplicación) → 404.
  - `IllegalArgumentException` y fallos de validación → 400.
  - Formato `ProblemDetail` (RFC 9457), que Spring 7 trae de serie.
- **D4:** `presentation` puede conocer las excepciones de dominio y la capa `application`. La base `NotFoundException` vive en `application`.
- **Spring Boot 4.1 usa Jackson 3** (`tools.jackson`). La integración de Immutables con Jackson está pensada para Jackson 2, y eso es el riesgo del ADR 0003.
- **Thymeleaf no está en el `pom.xml`.** Es una dependencia prevista (ADR 0005).
- **`@redocly/cli` 2.x necesita Node 20.19 o superior.** El PC de desarrollo se actualizó a Node 24.22.1 (npm 11.19.1) el 2026-10-23, así que el lint de OpenAPI se ejecuta en local y también en el CI (Node 22).
- Los tests de MVC usan `@WebMvcTest`. El 404 de la web pasa por el mecanismo de errores de Spring Boot (`templates/error/404.html`), que `MockMvc` no ejecuta, así que se prueba en `DddLabApplicationTests`, que es el único test de contexto completo según `src/test/CLAUDE.md`.

| Capa / paquete | Archivo | Cambio |
| -------------- | ------- | ------ |
| `application` | `src/main/java/com/pronoide/ddd/application/NotFoundException.java` | Base de los errores "no existe" |
| `presentation.rest` | `src/main/java/com/pronoide/ddd/presentation/rest/RestExceptionHandler.java` | `@RestControllerAdvice` único |
| tests | `src/test/java/com/pronoide/ddd/presentation/rest/RestExceptionHandlerTests.java` (+ controlador de prueba) | Nuevo |
| tests | `src/test/java/com/pronoide/ddd/presentation/rest/JacksonImmutablesTests.java` | Nuevo (riesgo del ADR 0003) |
| docs / CI | `docs/api/openapi.yaml`, `redocly.yaml`, `.github/workflows/ci.yml` | OpenAPI inicial y lint en CI |
| `presentation.web` | `pom.xml`, `src/main/resources/templates/fragments/layout.html`, `templates/error/404.html`, `static/css/app.css` | Thymeleaf, plantilla base y 404 |
| tests | `src/test/java/com/pronoide/ddd/DddLabApplicationTests.java` | Prueba de la página 404 |
| docs | `docs/adr/0003-lombok-immutables.md`, `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md` | Resultado de Jackson 3, término "Recurso inexistente" y enlace a la implementación |

## Tareas

### [x] T1 — Reglas de negocio → 422 con ProblemDetail (~10 min)

- **Lado:** back
- **Capa:** presentation
- **Archivos:** `presentation/rest/RestExceptionHandler.java`, `presentation/rest/RestExceptionHandlerTests.java` (+ un controlador de prueba en `src/test/.../presentation/rest/support/`)
- **Depende de:** —
- **Qué hacer:**
  1. **Rojo:** `@WebMvcTest` del controlador de prueba, que lanza una `InsufficientInitialDepositException` real. Se espera un 422 con `Content-Type: application/problem+json`, el mensaje del dominio en `detail` y un `title` fijo ("Regla de negocio incumplida").
  2. **Verde:** `@RestControllerAdvice(annotations = RestController.class)` que extiende `ResponseEntityExceptionHandler` y tiene un `@ExceptionHandler(DomainException.class)`.
- **Criterios de aceptación:**
  - [x] El test pasa y `./mvnw verify` está en verde.

### [x] T2 — Recurso inexistente → 404 y argumentos inválidos → 400 (~10 min)

- **Lado:** back
- **Capa:** application · presentation
- **Archivos:** `application/NotFoundException.java`, `RestExceptionHandler.java`, `RestExceptionHandlerTests.java`
- **Depende de:** T1
- **Qué hacer:**
  1. **Rojo:** tests para tres casos:
     - una subclase de prueba de `NotFoundException` → 404;
     - `IllegalArgumentException` (por ejemplo, un importe no positivo del dominio) → 400;
     - un cuerpo con `@Valid` incumplido → 400 con los campos que fallan.
  2. **Verde:** `NotFoundException` abstracta en `application` (con su mensaje) y los handlers correspondientes. La validación ya la cubre `ResponseEntityExceptionHandler`.
- **Criterios de aceptación:**
  - [x] Los tres casos devuelven el código y un `ProblemDetail` con `title` y `detail`.
  - [x] Ningún error de los cubiertos acaba en 500.

### [x] T3 — Comprobar Immutables con Jackson 3 (~10 min)

- **Lado:** back
- **Capa:** presentation (tests) · docs
- **Archivos:** `presentation/rest/JacksonImmutablesTests.java`, `docs/adr/0003-lombok-immutables.md`
- **Depende de:** —
- **Qué hacer:**
  1. Test con el `ObjectMapper`/`JsonMapper` de la aplicación (`@JsonTest` o el *bean* de Spring) que serializa y deserializa un DTO de prueba hecho con Immutables y `@ApplicationValue`.
  2. **Si funciona:** fijar en el ADR 0003 la forma exacta de anotarlo (por ejemplo, `@JsonSerialize`/`@JsonDeserialize`, o el builder con `@JsonPOJOBuilder`) y dejar el test como ejemplo.
  3. **Si no funciona:** sustituir el test por uno equivalente con un `record`, y decidir en el ADR 0003 que **los DTOs REST serán `record`**. Immutables se queda en los commands y vistas de `application`.
- **Criterios de aceptación:**
  - [x] Hay un test en verde que demuestra el tipo de DTO elegido (ida y vuelta JSON).
  - [x] El ADR 0003 recoge el resultado y la regla para los DTOs de la #5 en adelante.

### [x] T4 — OpenAPI inicial y lint en CI (~10 min)

- **Lado:** back
- **Capa:** docs · CI
- **Archivos:** `docs/api/openapi.yaml`, `redocly.yaml`, `.github/workflows/ci.yml`
- **Depende de:** T2
- **Qué hacer:**
  1. `openapi.yaml` 3.1 con:
     - `info`, servidor `http://localhost:8080` y `paths: {}`;
     - `components.schemas.ProblemDetail`;
     - `components.responses` reutilizables (`BadRequest`, `NotFound` y `UnprocessableEntity`) con ejemplos de los mensajes reales.
  2. `redocly.yaml` con la configuración `recommended`, desactivando solo las reglas que exigen operaciones (el documento aún no tiene rutas) y justificándolo en un comentario.
  3. Paso nuevo en `ci.yml`: `npx @redocly/cli lint docs/api/openapi.yaml`.
- **Criterios de aceptación:**
  - [x] `npx @redocly/cli lint docs/api/openapi.yaml` pasa en local y en el paso de lint del CI de la PR.
  - [x] Las respuestas de error documentadas coinciden con las que devuelve `RestExceptionHandler` (mismos códigos y `title`).

### [x] T5 — Thymeleaf y plantilla base (~10 min)

- **Lado:** web
- **Capa:** presentation
- **Archivos:** `pom.xml`, `templates/fragments/layout.html`, `static/css/app.css`
- **Depende de:** —
- **Qué hacer:**
  1. Añadir `spring-boot-starter-thymeleaf` (y su `-test` si Spring Boot 4 lo separa, como pasa con `webmvc`).
  2. Fragmentos de plantilla (`head`, `header`, `footer`) con el nombre de la aplicación y un CSS mínimo propio, sin frameworks de frontend ni el *layout dialect* (con los fragmentos nativos de Thymeleaf basta).
- **Criterios de aceptación:**
  - [x] `./mvnw verify` en verde con la nueva dependencia.
  - [x] Los fragmentos se usan desde la plantilla de T6, que prueba que se resuelven.

### [x] T6 — Página 404 propia (~10 min)

- **Lado:** web
- **Capa:** presentation
- **Archivos:** `templates/error/404.html`, `DddLabApplicationTests.java`
- **Depende de:** T5
- **Qué hacer:**
  1. **Rojo:** en `DddLabApplicationTests` (con `webEnvironment = RANDOM_PORT`), una petición `GET /no-existe` con `Accept: text/html` espera un 404 y el texto "Página no encontrada". Se mantiene el `contextLoads`.
  2. **Verde:** `templates/error/404.html` con los fragmentos de T5 y un enlace a la página de inicio.
- **Criterios de aceptación:**
  - [x] El test pasa. Una ruta inexistente vista en el navegador muestra la página propia.
  - [x] Una petición a una ruta inexistente de `/api/...` sin `Accept: text/html` sigue respondiendo en JSON.

### [ ] T7 — Documentación (~5 min)

- **Lado:** ambos
- **Capa:** docs
- **Archivos:** `docs/dominio/glosario.md`, `docs/arquitectura/fase-1-capas.md`
- **Depende de:** T6
- **Qué hacer:**
  1. Glosario: añadir "Recurso inexistente" (`NotFoundException`, error de aplicación → 404).
  2. `fase-1-capas.md`: enlazar la sección "Traducción de errores" con `RestExceptionHandler` y `openapi.yaml`, y anotar la decisión de T3 sobre los DTOs.
- **Criterios de aceptación:**
  - [ ] Los enlaces relativos apuntan a archivos que existen.

## Criterios de aceptación globales

**Específicos:**

- [ ] Tests `@WebMvcTest` del `@RestControllerAdvice` con un controlador de prueba: 422, 404 y 400 con `ProblemDetail`.
- [ ] `npx @redocly/cli lint docs/api/openapi.yaml` sin errores (local y CI).
- [ ] Una ruta inexistente de la web muestra la página 404 propia.
- [ ] ADR 0003 actualizado con el resultado de Jackson 3.

**Fijos:**

- [ ] Todas las tareas y sus criterios están marcados como cumplidos.
- [ ] `./mvnw verify` termina en `BUILD SUCCESS` (JUnit + ArchUnit).
- [ ] `./mvnw spring-boot:run` arranca y `GET /actuator/health` responde `{"status":"UP"}`.
- [ ] Se respeta la arquitectura de la fase: `presentation` solo depende de `application` y de las excepciones de dominio (D4). La regla de ArchUnit llega en la #12.
- [ ] El dominio no depende de Spring, `jakarta` ni `org.immutables`, y no usa `@Data`, `@Setter`, `@Builder` ni `@AllArgsConstructor` (`check-domain.mjs` sin errores).
- [ ] Los tests nuevos siguen `src/test/CLAUDE.md` (`snake_case` + `@DisplayName` en español).
- [ ] Solo se añade la dependencia prevista: Thymeleaf (ADR 0005).
- [ ] Si se toca la API: `docs/api/openapi.yaml` refleja las respuestas de error y el lint pasa.
- [ ] El término nuevo "Recurso inexistente" está en `docs/dominio/glosario.md`.

## Riesgos y preguntas abiertas

- **Immutables con Jackson 3 (T3):** el resultado puede obligar a usar `record` en los DTOs REST. Cualquiera de las dos salidas es válida y queda decidida antes del contrato de la #5.
- **Spring Boot 4 puede separar el soporte de test de Thymeleaf** en un artefacto `-test`, como hace con `webmvc`. Se comprueba en T5.

# Fase 1 · Monolito en capas (rama `Monolithic`)

Diseño objetivo de la fase 1, decidido en el [ADR 0004](../adr/0004-arquitectura-en-capas.md). Los términos usados aquí son los del [glosario](../dominio/glosario.md). Se completa con el estado real al cerrar la fase (#13).

## Capas

```mermaid
flowchart TD
    P["presentation<br/>web (Thymeleaf) · rest"] --> A["application<br/>servicios de aplicación, commands y vistas"]
    A --> D["domain<br/>model · service"]
    A --> R["persistence<br/>repositorios en memoria"]
    R --> D
    P -. "solo excepciones de dominio" .-> D
```

Las flechas indican **quién depende de quién**: siempre hacia abajo. `domain` está en el fondo y no depende de nada. La flecha punteada es la única dependencia de `presentation` con el dominio: las excepciones que traduce a HTTP o a mensajes (D4).

| Paquete | Responsabilidad | Contiene |
| ------- | --------------- | -------- |
| `presentation.web` | Pantallas y formularios: recoge la entrada, llama a un caso de uso y pinta la vista | Controladores Spring MVC, plantillas Thymeleaf |
| `presentation.rest` | API REST: traduce HTTP ↔ commands/vistas y los errores a `ProblemDetail` desde un único punto | `@RestController`, DTOs, `@RestControllerAdvice` |
| `application` | Casos de uso: carga agregados, invoca al dominio, guarda. **Sin reglas de negocio** | Servicios de aplicación, commands y vistas (Immutables, `@ApplicationValue`), errores de aplicación |
| `domain.model` | Modelo y reglas de negocio (R1-R15 del glosario) | Agregado `CheckingAccount`, entidades, objetos de valor, excepciones de dominio |
| `domain.service` | Reglas que abarcan varios agregados | `MoneyTransferService` (#7) |
| `persistence` | Guardar y recuperar agregados | `InMemoryCheckingAccountRepository` (clase concreta, simulada) |

## Dependencias

Esta tabla es la especificación de [`LayeredArchitectureTests`](../../src/test/java/com/pronoide/ddd/LayeredArchitectureTests.java) (#12). Cada fila es una regla de ArchUnit.

| # | Origen | Puede depender de | No puede depender de |
| - | ------ | ----------------- | -------------------- |
| D1 | `..domain..` | solo el JDK | `..application..`, `..persistence..`, `..presentation..`, `org.springframework..`, `jakarta..`, `org.immutables..` |
| D2 | `..persistence..` | `..domain..` | `..application..`, `..presentation..` |
| D3 | `..application..` | `..domain..`, `..persistence..` | `..presentation..` |
| D4 | `..presentation..` | `..application..`; de `..domain.model..` **solo** `DomainException` y sus subclases (para traducirlas a HTTP o a mensajes) | `..persistence..`, `..domain.service..` y el resto de `..domain.model..` (agregados, entidades, objetos de valor): la presentación trabaja con commands y vistas, no con el modelo |
| D5 | `..presentation.web..` | — | `..presentation.rest..` (y al revés: la web y REST no se conocen) |

Además, ya existen y se mantienen: el dominio no depende del framework y su estado es privado (`DomainIsolationTests`).

## Ejemplo de flujo: un ingreso

`POST /api/accounts/{id}/deposits` (ruta definida en #6):

```mermaid
sequenceDiagram
    participant C as Cliente HTTP
    participant RC as presentation.rest<br/>AccountController
    participant AS as application<br/>CheckingAccountService
    participant RP as persistence<br/>InMemoryCheckingAccountRepository
    participant AG as domain.model<br/>CheckingAccount
    C->>RC: POST {amount, currency}
    RC->>RC: valida el formato y que el importe sea positivo (400 si falla)
    RC->>AS: deposit(DepositCommand)
    AS->>RP: findById(id)
    RP-->>AS: CheckingAccount (o vacío → AccountNotFoundException → 404)
    AS->>AG: deposit(MonetaryAmount)
    AG->>AG: R3 importe positivo (IllegalArgumentException → 400) · R4 divisa (CurrencyMismatchException → 422)
    AG-->>AS: Transaction aprobada
    AS->>RP: save(account)
    AS-->>RC: vista del movimiento
    RC-->>C: 201 + vista
```

| Responsabilidad | Dónde vive |
| --------------- | ---------- |
| Validar el formato de la petición | `presentation` |
| Cargar y guardar el agregado | `application` (usando `persistence`) |
| Reglas de negocio (importe positivo, divisa, descubierto…) | `domain.model` |
| Traducir errores a HTTP o a mensajes de pantalla | `presentation` (un único punto por canal) |

**Traducción de errores.** Solo las subclases de `DomainException` son reglas de negocio (422). Los argumentos inválidos que el dominio rechaza con `IllegalArgumentException` (importe no positivo, IBAN mal formado) **no** son `DomainException`: la presentación los valida antes de llamar al caso de uso, y si aun así llegan, se traducen a 400 como error de validación. `AccountNotFoundException` (aplicación, subclase de `NotFoundException`) se traduce a 404.

Implementado en la #8:

- API REST: [`RestExceptionHandler`](../../src/main/java/com/pronoide/ddd/presentation/rest/RestExceptionHandler.java), único `@RestControllerAdvice`, devuelve `ProblemDetail` (RFC 9457). Las respuestas están documentadas en [`openapi.yaml`](../api/openapi.yaml) (`BadRequest`, `NotFound`, `UnprocessableContent`) y el CI las valida con Redocly.
- Web: [página 404 propia](../../src/main/resources/templates/error/404.html) sobre los [fragmentos de la plantilla base](../../src/main/resources/templates/fragments/layout.html). Las rutas de la API siguen respondiendo en JSON.
- **DTOs REST:** pueden ser de Immutables con Jackson 3 (`@JsonSerialize`/`@JsonDeserialize` de `tools.jackson.databind.annotation`); ver el [ADR 0003](../adr/0003-lombok-immutables.md).

Implementado en la #5 (contrato: [`open-query-list-accounts.md`](../contracts/open-query-list-accounts.md)):

- `application`: [`CheckingAccountService`](../../src/main/java/com/pronoide/ddd/application/CheckingAccountService.java) (abrir, consultar y listar), `OpenAccountCommand`, las vistas `AccountSummary`, `AccountDetails` y `TransactionSummary`, y `AccountNotFoundException`.
- `domain.model`: `DuplicateAccountNumberException` (R16), lanzada por el servicio tras consultar el repositorio.
- `presentation.rest`: `AccountController` (`/api/accounts`), con la petición como `record` con Bean Validation y las respuestas como DTOs de Immutables.
- `presentation.web`: listado en `/` y alta en `/accounts/new` (Post/Redirect/Get con mensaje flash). La sesión se sigue solo por cookie (`server.servlet.session.tracking-modes=cookie`): con seguimiento por URL, la redirección iba a `/;jsessionid=…` y Spring respondía 404.
- La petición REST y el formulario aplican la misma validación, para que lo que aceptan nunca provoque un `IllegalArgumentException` del dominio.

Implementado en la #6 (contrato: [`deposits-withdrawals.md`](../contracts/deposits-withdrawals.md)):

- `application`: `CheckingAccountService.deposit` y `withdraw`, con `DepositCommand` y `WithdrawCommand`; devuelven el movimiento (`TransactionSummary`). Una retirada no permitida se devuelve como `REJECTED`, no como excepción.
- `presentation.rest`: `POST /api/accounts/{id}/deposits` y `/withdrawals` (201 con el movimiento, también si se rechaza).
- `presentation.web`: detalle de la cuenta en `/accounts/{id}` con sus movimientos y los formularios de ingreso y retirada. Un `@ControllerAdvice` acotado al paquete `presentation.web` (`basePackageClasses`) pinta la página 404 para `NotFoundException`; no se acota por `@Controller` porque `@RestController` también la lleva y capturaría los errores de la API.
- Un importe no positivo es un 400 o un error de campo (R3), no un error de dominio; solo la divisa distinta es 422.

Implementado en la #7 (contrato: [`money-transfers.md`](../contracts/money-transfers.md)):

- `domain.service`: [`MoneyTransferService`](../../src/main/java/com/pronoide/ddd/domain/service/MoneyTransferService.java), primer servicio de dominio, Java puro y sin estado (R17-R19). `CheckingAccountService` lo crea con `new`, sin bean de Spring, para que el dominio no dependa del framework (D1).
- `domain.model`: `SameAccountTransferException` (R17).
- `application`: `CheckingAccountService.transfer` con `TransferCommand` y la vista `TransferSummary`; carga las dos cuentas, delega en el servicio de dominio y **guarda las dos**.
- `presentation.rest`: `TransferController` (`POST /api/transfers`, 201 también si se rechaza).
- `presentation.web`: formulario de transferencia en el detalle de la cuenta, con la cuenta destino elegida entre las demás (`listAccounts()`); la presentación no conoce `domain.service` (D4).

Implementado en la #11:

- `application`: [`DemoDataLoader`](../../src/main/java/com/pronoide/ddd/application/DemoDataLoader.java), un `ApplicationRunner` con `@Profile("demo")` que abre tres cuentas (dos minoristas y una de alto patrimonio) con `CheckingAccountService.openAccount`, nunca con el repositorio, así que los datos cumplen las mismas reglas que los reales.
- `pom.xml`: la propiedad `spring-boot.run.profiles=demo` hace que `./mvnw spring-boot:run` arranque con esos datos; `-Dspring-boot.run.profiles=default` lo evita. Los tests no activan el perfil (`DemoProfileTests` lo activa a propósito).
- Tests: el builder `TestAccounts` (`@Builder` de Lombok, paquete de test `support`) abre cuentas con `CheckingAccount.open` y valores por defecto; sustituye los helpers repetidos de los tests de la cuenta, del repositorio y del servicio de transferencias. Se usa como `TestAccounts.anAccount()…open()`, sin import estático: javac resuelve los import estáticos antes de que Lombok genere el método.

Implementado en la #12 (`LayeredArchitectureTests`, cada regla validada rompiéndola temporalmente):

| Regla | Cubre |
| ----- | ----- |
| `las_capas_solo_dependen_hacia_abajo` (`layeredArchitecture()`) | D2, D3: `presentation` no la usa nadie, `application` solo `presentation`, `persistence` solo `application` |
| `el_dominio_solo_depende_del_jdk` | D1 (incluye Spring, `jakarta` y `org.immutables`) |
| `la_presentacion_solo_conoce_las_excepciones_del_dominio` | D4 (de `domain`, solo `DomainException` y sus subclases; tampoco `domain.service`) |
| `la_web_y_rest_no_se_conocen` (`slices()`) | D5, en los dos sentidos |
| `el_dominio_no_tiene_setters_publicos` | El dominio cambia de estado solo con métodos de negocio |
| `los_controladores_no_acceden_al_repositorio` | Clases con `@Controller` o `@RestController` (meta-anotación) sin `persistence`; se solapa con la regla de capas, pero su mensaje es más claro |
| `toda_clase_pertenece_a_una_capa` | Fuera de las cuatro capas solo está `DddLabApplication`: la regla de capas ignora los paquetes que no son capas, así que un paquete nuevo (`config`…) tendría que añadirse antes como capa |

Implementado en la #48 (rediseño de la web, solo `presentation.web` y plantillas):

- Tema oscuro y minimalista en `static/css/app.css` (variables CSS, sin dependencias ni JavaScript): saldo protagonista, listas de filas en lugar de tablas, acciones como pastillas.
- [`WebFormat`](../../src/main/java/com/pronoide/ddd/presentation/web/WebFormat.java) (`@Component("fmt")`, usable como `${@fmt.money(...)}`): importes en formato español con separador de miles siempre (`1.000,00 €`; `es-ES` de Java no agrupa las cifras de 4 dígitos), importes con signo, IBAN abreviado e iniciales. Solo recibe valores de las vistas, nunca tipos del dominio (D4).
- Ingresar, retirar y transferir son `<details>` con el formulario dentro: se abre al pulsar la pastilla, y tras un POST solo se abre el que vuelve con errores (`th:object` en el `<details>` y `th:open="${#fields.hasAnyErrors()}"`).
- El listado no muestra el segmento: `AccountSummary` no lo tiene y añadirlo cambiaría `application`.

## Issues de la fase

| Orden | Issue | Capas | Clases que introduce |
| ----- | ----- | ----- | -------------------- |
| 1 | #3 (esta documentación) | docs | — |
| 2 | #4 Persistencia en memoria | `persistence` | `InMemoryCheckingAccountRepository` |
| 3 | #8 Base de REST y web | `presentation.rest`, `presentation.web` | `@RestControllerAdvice`, plantilla base, página 404, `openapi.yaml` inicial |
| 4 | #5 Abrir, consultar y listar cuentas (equipo) | `application`, `presentation.rest`, `presentation.web` | `CheckingAccountService`, `OpenAccountCommand`, `AccountSummary`, `AccountDetails`, `AccountNotFoundException` |
| 5 | #6 Ingresos y retiradas (equipo) | `application`, `presentation.rest`, `presentation.web` | `DepositCommand`, `WithdrawCommand`, vista del movimiento |
| 6 | #7 Transferencias (equipo) | `domain.service`, `application`, `presentation.rest`, `presentation.web` | `MoneyTransferService`, `TransferCommand` |
| 7 | #11 Datos de demostración | `application` | `ApplicationRunner` con perfil `demo` |
| 8 | #12 Reglas de ArchUnit | tests | `LayeredArchitectureTests` (reglas D1-D5) |
| 9 | #13 Cierre de la fase | docs | etiqueta `v1-monolito`, rama `Hexagonal` |

Las issues marcadas como *equipo* se implementan con `/implement-plan-team` (contrato primero, ADR 0007).

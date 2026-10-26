# Estado del proyecto

> **Léelo al empezar cada sesión.** Es el punto de entrada para continuar sin depender de conversaciones anteriores. **Se actualiza en cada PR que cierra una issue** (lo exigen `implement-plan`, `implement-plan-team`, la plantilla de los planes y el CI).

- **Última actualización:** 2026-10-26 · PR #53 (ADR 0009, dominio sin Lombok) e issue #54
- **Rama activa:** `Monolithic` (fase 1). `master` congelada. `Hexagonal` aún no existe.

## Dónde estamos

| Fase | Hecho | En curso | Pendiente |
| ---- | ----- | -------- | --------- |
| 1 · Monolito en capas | #2 Lombok/Immutables · #3 glosario, capas y ADR 0004 · #4 repositorio en memoria · #8 base REST + web · #5 abrir, consultar y listar cuentas · #31 flujo en equipo (cerrada tras la #5) · #6 ingresos y retiradas con detalle de la cuenta · #7 transferencias entre cuentas (PR #43) · regla deny redundante de los hooks (PR #44, sin issue) · #11 datos de demostración (PR #45) · #12 reglas de ArchUnit de las capas (PR #47) · #48 rediseño oscuro de la web (PR #49) · #50 login con roles (PR #51) · análisis funcional y técnico + `check-domain` de `@EqualsAndHashCode` (PR #52, sin issue) | ADR 0009, dominio sin Lombok (PR #53, sin issue, pendiente de fusión) | #54 → #13 |
| 2 · Hexagonal | — | — | #14 → #23 (se empieza al cerrar #13, creando `Hexagonal` desde `Monolithic`) |

## Siguiente paso

1. Revisar y fusionar la PR #53 (ADR 0009) (lo decide el usuario).
2. `/plan-feature 54` (reglas de ArchUnit pendientes de los ADR 0004 y 0008 y limpieza de `DomainIsolationTests`).
3. `/plan-feature 13` (cerrar la fase 1: documentación final y etiqueta `v1-monolito`).

Orden completo de la fase 1 y por qué: `docs/arquitectura/fase-1-capas.md`, sección "Issues de la fase".

## Acuerdos de trabajo con el usuario

- **Siempre se planifica antes de implementar** (`/plan-feature`) y el usuario aprueba el plan.
- **Las fusiones las decide el usuario.** No se fusiona ninguna PR sin su "sí".
- **`CLAUDE.md` y la configuración de `.claude/`** solo se cambian con su visto bueno explícito, nunca a petición de un agente.
- **Los diagramas se le enseñan antes de commitearlos.** Estilo de los diagramas: escala de grises, sin colores.
- El usuario puede seguir la sesión desde el móvil (Remote Control). **Los avisos por Telegram están desactivados por el usuario hasta nuevo aviso** (2026-10-23): no se ejecuta `scripts/notify.mjs` aunque una skill lo pida.
- Idioma: conversación, commits, `@DisplayName` y mensajes de error del dominio en español; código y **documentación en inglés** (desde la PR de convención de idioma, 2026-10-23). Los documentos existentes en español se traducirán en una issue aparte.

## Decisiones ya tomadas que afectan a lo que viene

- **Contrato web ↔ back** (ADR 0007, enfoque A): servicio, commands y vistas de `application`, más un fragmento de OpenAPI. La web no consume la API REST.
- **DTOs REST:** pueden ser de Immutables con `@JsonSerialize`/`@JsonDeserialize` de `tools.jackson.databind.annotation` (ADR 0003).
- **Errores:** `DomainException` → 422, `NotFoundException` → 404, `IllegalArgumentException` y validación → 400 (`RestExceptionHandler`). `AccountNotFoundException` (#5) hereda de `NotFoundException`.
- **Número de cuenta único (R16):** `CheckingAccountService.openAccount` lo comprueba con `findByAccountNumber` y lanza `DuplicateAccountNumberException` (422). El `IllegalStateException` del repositorio queda solo para la carrera entre dos altas simultáneas (500, limitación aceptada).
- **Los commands validan antes de llamar al dominio.** El 400 por `IllegalArgumentException` es solo una red de seguridad.
- **Validación compartida:** la petición REST (`record` con Bean Validation) y el formulario web aplican las mismas restricciones (contrato de la #5), y la web devuelve al formulario el rechazo del command en lugar de un 500.
- **Sesión web solo por cookie** (`server.servlet.session.tracking-modes=cookie`): con seguimiento por URL, la redirección tras un Post/Redirect/Get iba a `/;jsessionid=…` y Spring respondía 404.
- **Segmentos y otros enumerados del dominio** viajan como `String` en commands y vistas (D4); un test comprueba que `OpenAccountCommandDef.SEGMENTS` coincide con `CustomerSegment`.
- **Retirada rechazada:** no es un error. El servicio devuelve el movimiento con `REJECTED` (REST 201) y la web lo muestra como aviso, con un texto válido para los dos segmentos.
- **Importe no positivo = 400 / error de campo** (R3), no error de dominio; el command también rechaza un importe que redondea a cero en su divisa (`0.001` EUR). Decidido por el usuario el 2026-10-23 al planificar la #6.
- **404 en la web:** `@ControllerAdvice` acotado con `basePackageClasses` a `presentation.web`. No acotarlo por `@Controller`: `@RestController` también la lleva y capturaría los errores de la API.
- **Transferencias (#7):** viven en el servicio de dominio `MoneyTransferService` (Java puro, sin estado), que `CheckingAccountService` crea con `new` (sin bean: en la fase 2 pasará a `config`). Orden: misma cuenta (R17, 422) → divisa del destino (R19, 422) → retirada en origen → ingreso en destino solo si se aprueba (R18). Una transferencia rechazada es 201 con `REJECTED`, como la retirada. Se registra como `WITHDRAWAL` + `DEPOSIT`, sin tipos propios. Cuenta destino inexistente = 404. `POST /api/transfers` en un `TransferController` propio.
- **Dominio sin Lombok (ADR 0009, 2026-10-26):** sustituye la parte del dominio del ADR 0003. `@Getter` en `domain..` rompía D1 por `@lombok.Generated`; ahora `check-domain.mjs` rechaza cualquier `import lombok`/`@lombok.` allí y la plantilla de entidad escribe a mano accesores y `equals`/`hashCode` por id.
- **Login y roles (#50, ADR 0008):** Spring Security autentica (formulario y sesión en la web, HTTP Basic sin sesión en `/api`) y comprueba roles en las URL; la propiedad de las cuentas la comprueba `CheckingAccountService` con el `Caller`. Cuenta ajena = 404; falta de rol = 403. El propietario es un nombre de usuario guardado en `persistence`, nunca en el dominio. Las transferencias van a un IBAN escrito (`targetAccountNumber`, cambio incompatible de la API). Usuarios demo con hash BCrypt; administrador real por variables de entorno.

## Pendientes sueltos

- Traducir al inglés la documentación existente (`docs/`, `README`), que sigue en español (issue aparte).
- Las adiciones de la #5 a documentos que siguen en español (glosario, `fase-1-capas.md`, este estado) se escribieron en español para no mezclar idiomas; se traducen con el resto.
- Telegram: los avisos locales se silencian con `TELEGRAM_DISABLED=1` (PR #40), activado en `~/.claude/settings.json` del usuario. El aviso de CI fallida sigue activo (usa los secretos de GitHub).
- **Datos de demostración (#11):** `./mvnw spring-boot:run` arranca con el perfil `demo` (propiedad `spring-boot.run.profiles` del `pom.xml`) y tres cuentas abiertas con los casos de uso; `-Dspring-boot.run.profiles=default` arranca sin datos. Proponer al usuario mencionarlo en la sección de comandos de `CLAUDE.md` (requiere su visto bueno).
- `DomainIsolationTests` prohíbe un paquete `com.pronoide.ddd.bootstrap..` que no existe (resto de una versión anterior). Inofensivo; se quita en la #54. Hoy lo cubre `el_dominio_solo_depende_del_jdk` (#12).
- **Web (#48):** el listado no muestra el segmento (falta en `AccountSummary`; sería una issue del lado back). Las transferencias aparecen como retirada e ingreso, sin etiqueta propia. El IBAN abreviado de los avisos puede partirse entre líneas en pantallas estrechas (se arreglaría con espacios de no separación en `WebFormat.maskedIban`). Los movimientos se listan del más antiguo al más reciente.
- **Login (#50):** la consola H2 (`/h2-console`) es solo para administradores (marcos del mismo origen, sin CSRF en sus formularios) y `/actuator/health` público solo da el estado; un usuario puede saber si un IBAN existe intentando una transferencia (riesgo aceptado en el ADR 0008); no hay límite de intentos de login. Sin gestión de usuarios (alta, cambio de contraseña): son estáticos. Una cuenta puede asignarse a un usuario que aún no existe.
- **Equipo (#50):** los teammates no pueden escribirse entre sí (sin `SendMessage`); el lead une sus revisiones del contrato. Los worktrees aislados siguen partiendo de `Monolithic`: el `git merge --ff-only` inicial funcionó.
- **`implement-plan-team` y los worktrees aislados (#7):** el worktree que crea el `Agent` con `isolation: worktree` parte de `Monolithic`, no de la rama de integración, y se borra solo si el agente termina sin cambios. Lo que funcionó: que el teammate haga primero `git merge --ff-only <rama-integración>`. Un hook de la sesión impide al lead escribir en otro worktree, así que el lead integra en su propio worktree. Conviene reflejarlo en la skill (requiere el visto bueno del usuario, porque toca `.claude/`).
- **Avisos de la revisión de la #7 para la fase 2:** guardar las dos cuentas de una transferencia en una transacción (hoy son dos `save` sin bloqueo; documentado en el glosario); valorar un `approved()` o un enumerado de `application` en lugar de comparar el literal `"REJECTED"` en la web; registrar `MoneyTransferService` como bean en `config`.

- **Aviso latente (revisión de la #6):** `CheckingAccount.tryWithdraw` registra el movimiento antes de evaluar `isAllowed`; en una cuenta que no fuera en EUR, un cliente de alto patrimonio provocaría `CurrencyMismatchException` (el límite está en EUR) con el movimiento `PENDING` ya registrado. Hoy no ocurre porque no se puede abrir una cuenta que no sea en EUR (el depósito mínimo también está en EUR). Arreglarlo evaluando antes de registrar cuando se admitan otras divisas.
- Test que falta: "el nombre del titular es obligatorio" (`AccountHolder`).
- Sugerencia pendiente de decisión del usuario: mencionar en la sección de la fase 1 de `CLAUDE.md` que `presentation` puede conocer las excepciones de dominio (regla D4).

## Entorno

- Windows 11, Java 21 (Temurin), **Node 24.22.1** con npm 11.19.1, `gh` autenticado como `dawnrock`.
- El repositorio está dentro de OneDrive: si un worktree no se puede borrar porque está bloqueado, se borra su carpeta después de `git worktree remove`.
- Secretos del repositorio: `CLAUDE_CODE_OAUTH_TOKEN`, `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID`. En local, Telegram se configura en `.claude/settings.local.json`.
- `Monolithic` está protegida (solo por PR, con `verify` obligatorio). `master` está bloqueada.
- Si la revisión de Claude falla con `is_error: true` en unos 2 segundos, la causa conocida es un `CLAUDE_CODE_OAUTH_TOKEN` mal copiado (401). Se diagnostica con `show_full_output` (ver la historia de las PRs #28-#30).

## Historial

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
| 2026-10-23 | #39 | #5 | Abrir, consultar y listar cuentas (REST + web), primera issue en equipo |
| 2026-10-23 | #40 | — | Interruptor `TELEGRAM_DISABLED` para silenciar los avisos |
| 2026-10-24 | #41 | #6 | Ingresos y retiradas con detalle de la cuenta (REST + web); cierra el pendiente de sinónimos heredados |
| 2026-10-25 | #43 | #7 | Transferencias entre cuentas (REST + web): primer servicio de dominio `MoneyTransferService` (R17-R19) |
| 2026-10-25 | #44 | — | Quita la regla deny `Write(./.claude/hooks/**)`, que nunca se aplicaba |
| 2026-10-25 | #45 | #11 | Datos de demostración con el perfil `demo` y builder de cuentas de test `TestAccounts` |
| 2026-10-25 | #46 | — | Quita del estado el pendiente resuelto de las ramas de los teammates |
| 2026-10-25 | #47 | #12 | `LayeredArchitectureTests`: D1-D5 de la fase 1, sin setters públicos en el dominio y controladores sin repositorio |
| 2026-10-25 | #49 | #48 | Rediseño oscuro y minimalista de la web, `WebFormat` e importes en formato español |
| 2026-10-25 | #51 | #50 | Login con roles admin y usuario (Spring Security, ADR 0008), propiedad de cuentas y transferencia por IBAN |

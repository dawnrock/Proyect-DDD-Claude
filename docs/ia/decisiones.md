# Decisiones del proyecto

> Anexo de [`estado-del-proyecto.md`](estado-del-proyecto.md). **No se lee al empezar cada sesión**: consúltalo cuando la tarea toque el área de una decisión (planificar, implementar o revisar). Las decisiones de arquitectura de fondo están en los ADR (`docs/adr/`); aquí van las de detalle que afectan a las próximas issues.

## Decisiones ya tomadas que afectan a lo que viene

- **Fin de la fase 1 (#13):** `Monolithic` queda congelada en la etiqueta `v1-monolito`, como `master` (solo se toca por decisión expresa del usuario). `Hexagonal` se crea desde `Monolithic` y pasa a ser la rama por defecto del repositorio: las PR de la fase 2 van contra ella.
- **Paquetes de la fase 2 (#14, ADR 0006):** `presentation` → `adapter.in` (el soporte de seguridad común, en su raíz) y `persistence` → `adapter.out.persistence`. Provisional: `application` usa `adapter.out.persistence` hasta la #15 y `LayeredArchitectureTests` solo ajusta sus patrones hasta la #22.
- **Issues de la fase 2 actualizadas (2026-10-30):** la #15 añade un puerto en `application` para la propiedad de las cuentas (`InMemoryAccountOwnershipRepository`); la #18 cambia el contrato de apertura (IBAN generado, segmento decidido por la factoría) y pasa a ser de equipo (back + web).

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
- **Graphify solo para dependencias (#57, ADR 0010):** el grafo del código (`graphify-out/`, no versionado, uno por worktree) se consulta con `node scripts/graphify.mjs affected|path` para saber quién depende de una clase; para el resto se sigue usando Grep. Sin hook: sus avisos costaban más de lo que ahorraban. `implement-plan` regenera el grafo al crear el worktree (`node scripts/graphify.mjs update .`).
- **Estado dividido (#57):** `estado-del-proyecto.md` se mantiene corto (se lee en cada sesión); las decisiones, los pendientes y el historial van a sus anexos.

## Entorno

- Windows 11, Java 21 (Temurin), **Node 24.22.1** con npm 11.19.1, `gh` autenticado como `dawnrock`.
- El repositorio está dentro de OneDrive: si un worktree no se puede borrar porque está bloqueado, se borra su carpeta después de `git worktree remove`.
- Secretos del repositorio: `CLAUDE_CODE_OAUTH_TOKEN`, `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID`. En local, Telegram se configura en `.claude/settings.local.json`.
- `Monolithic` está protegida (solo por PR, con `verify` obligatorio). `master` está bloqueada.
- Si la revisión de Claude falla con `is_error: true` en unos 2 segundos, la causa conocida es un `CLAUDE_CODE_OAUTH_TOKEN` mal copiado (401). Se diagnostica con `show_full_output` (ver la historia de las PRs #28-#30).
- Graphify 0.9.97 (`py -m pip install --user graphifyy`, Python 3.14 con el lanzador `py`). No está en el PATH: se usa siempre a través de `scripts/graphify.mjs`. Sin Graphify, todo sigue funcionando con Grep.

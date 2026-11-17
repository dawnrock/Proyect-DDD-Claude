# Análisis funcional

> **Excepción de idioma:** este documento está en castellano a petición del usuario, como excepción a la convención de escribir la documentación en inglés. No se traduce.

> Fotografía de la rama `Hexagonal` (fase 2, puertos y adaptadores) después del alta de clientes de Mon-A Bank (#103, PR #104). La fuente de verdad de los términos y las reglas es el [glosario](../dominio/glosario.md); este documento los agrupa por dominio y explica cómo encajan las piezas. `Monolithic` tiene su propia versión de este análisis: el comportamiento es el mismo, pero las reglas del alta están numeradas de R27 a R31 en lugar de R28 a R32.

## 1. Propósito de la aplicación

Una pequeña **aplicación bancaria de cuentas corrientes** del banco ficticio **Mon-A Bank**. Un visitante puede darse de alta como cliente desde la página de acceso, y un administrador también abre cuentas a los clientes; los clientes inician sesión, consultan su saldo y sus movimientos, ingresan, retiran y transfieren dinero a otras cuentas. Las reglas de negocio (depósito inicial mínimo, descubierto según el segmento del cliente, una sola divisa por cuenta…) viven en un modelo de dominio en Java puro construido con los patrones tácticos de DDD, en el centro de una arquitectura hexagonal.

Cuatro adaptadores de entrada llaman a los mismos casos de uso:

- **Web** (Thymeleaf, renderizada en el servidor), para personas.
- **API REST** (`/api/**`, documentada en [`openapi.yaml`](../api/openapi.yaml)), para máquinas.
- **Línea de comandos** (`DomainModelRunner`, perfil `demo`), que carga los datos de demostración.
- **Tarea programada** (`SegmentReviewScheduler`), que revisa los segmentos cada día a las 3:00.

## 2. Actores

| Actor | Rol | Qué puede hacer |
| ----- | --- | --------------- |
| Visitante | sin sesión | Se da de alta como cliente en tres pasos (datos, acceso, cuenta) o por `POST /api/customers`, y queda con la sesión iniciada. |
| Administrador | `ADMIN` | Ve todas las cuentas, abre cuentas a un cliente nuevo o existente (por su documento), cambia un IBAN por otro generado, opera sobre cualquier cuenta y usa la consola H2. |
| Cliente (usuario) | `USER` | Ve y opera **solo sus** cuentas, abre otra cuenta indicando solo el depósito y puede transferir a cualquier cuenta existente indicando su IBAN. |
| Sistema | `Caller.system()` interno | Carga los datos de demostración al arrancar (perfil `demo`). |
| Reloj | tarea programada, sin `Caller` | Revisa cada día el segmento de las cuentas a las que les toca. |

Un cliente que pide una cuenta que no es suya recibe un **404** (no un 403), para no revelar que existen las cuentas de otras personas.

## 3. Dominios (áreas delimitadas)

La aplicación tiene un dominio principal y tres áreas de apoyo. Solo el dominio principal se modela con DDD; las otras se mantienen fuera de `domain` a propósito y se comunican con él por puertos.

```
┌──────────────────────────────────────────────────────────────┐
│ Dominio principal: Cuentas corrientes   (domain.model)        │
│   CheckingAccount ─┬─ AccountHolder ─┬─ CustomerSegment      │
│                    │                 └─ TaxId                │
│                    ├─ AccountNumber                           │
│                    ├─ MonetaryAmount                          │
│                    └─ Transaction ─┬─ TransactionType         │
│                                    └─ TransactionStatus       │
│   Customer ─┬─ TaxId                                          │
│             └─ EmailAddress                                   │
│   CheckingAccountFactory · eventos de dominio                 │
│ Servicios de dominio                   (domain.service)       │
│   MoneyTransferService · SegmentReviewService                 │
└──────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────┐
│ Apoyo: agencia tributaria simulada   (adapter.out.taxagency)  │
│   FakeTaxAgencyIncomeVerifier, detrás del puerto IncomeVerifier│
└──────────────────────────────────────────────────────────────┘
┌───────────────────────────────┐ ┌────────────────────────────┐
│ Apoyo: Identidad y acceso     │ │ Apoyo: Datos de demostración│
│ (adapter.in + application)    │ │ (adapter.in.cli, perfil demo)│
│ usuario, roles, Caller,       │ │ DomainModelRunner          │
│ propietario de cuenta y de    │ │                            │
│ cliente                       │ │                            │
└───────────────────────────────┘ └────────────────────────────┘
```

### 3.1 Dominio principal · Cuentas corrientes

Todo lo relativo al dinero, los saldos, los clientes y las reglas que los protegen. No sabe nada de usuarios, HTTP, Spring ni persistencia: lo que necesita de fuera (guardar cuentas y clientes, numerar IBAN, verificar ingresos, publicar eventos) lo pide por **puertos de salida** que implementan los adaptadores.

### 3.2 Servicios de dominio

- **Transferencias:** una operación que implica **dos** agregados y que, por tanto, no pertenece a ninguno de ellos. `MoneyTransferService` es Java puro y sin estado: coordina dos `CheckingAccount` sin añadir reglas de saldo propias.
- **Revisión del segmento:** cuando a una cuenta le toca, `SegmentReviewService` vuelve a pedir los ingresos del titular por el puerto `IncomeVerifier` y le comunica el resultado a la cuenta, que conserva el segmento o vuelve a minorista.

### 3.3 Área de apoyo · Identidad y acceso

Quién usa la aplicación y de quién es cada cuenta. **No forma parte del dominio de cuentas** (ADR 0008):

- La autenticación y los roles los gestiona Spring Security en los adaptadores de entrada (`adapter.in`).
- El `Caller` (usuario + indicador de administrador) viaja hasta el servicio de aplicación.
- El propietario de cada cuenta lo guarda el puerto `AccountOwnershipRepository`, y el usuario de cada cliente, el puerto `CustomerOwnershipRepository`. Los dos viven en `application`, no en el dominio, y los implementan adaptadores en memoria.
- El alta crea el usuario en tiempo de ejecución: `CustomerRegistration` (`adapter.in`) pide el alta al caso de uso y después crea el usuario `USER` con su contraseña cifrada (ADR 0012). Los usuarios viven en memoria y se pierden al reiniciar.

### 3.4 Área de apoyo · Datos de demostración

Con el perfil `demo` (el que usa por defecto `./mvnw spring-boot:run`), `DomainModelRunner` registra a tres clientes y les abre una cuenta mediante los casos de uso normales, así que cumplen las mismas reglas. Los IBAN los asigna el banco y son siempre los mismos, porque la numeración empieza de cero con cada arranque:

| IBAN | Titular | Documento | Ingresos verificados | Segmento | Depósito inicial | Usuario |
| ---- | ------- | --------- | -------------------- | -------- | ---------------- | ------- |
| ES8099990001450000000001 | Ada Lovelace | 12345678Z | 40.000,00 € | RETAIL | 1.000,00 € | `ada` |
| ES1099990001410000000002 | Grace Hopper | 87654321X | 250.000,00 € | HIGH_NET_WORTH | 5.000,00 € | `grace` |
| ES8899990001440000000003 | Alan Turing | 11111111H | 25.000,00 € | RETAIL | 250,00 € | `alan` |

Después ingresa 200 € a Ada, retira 50 € de Alan (aprobada) y 1.000 € (rechazada) y transfiere 300 € de Grace a Ada. Saldos finales: Ada 1.500 €, Grace 4.700 €, Alan 200 €. Cada paso deja una línea `[demo]` en el log.

El segmento no se indica al abrir: lo deciden los ingresos verificados en la agencia tributaria simulada y el saldo. Grace Hopper es de alto patrimonio por sus ingresos (R20), aunque su depósito no llegue al patrimonio mínimo; su primera revisión es a los seis meses (R27).

## 4. Modelo de dominio: entidades y objetos de valor

| Concepto | Clase | Tipo DDD | Identidad | ¿Cambia? |
| -------- | ----- | -------- | --------- | -------- |
| Cuenta corriente | `CheckingAccount` | **Raíz del agregado** | `UUID id` | Sí, solo con sus métodos de negocio |
| Cliente | `Customer` | **Raíz del agregado** | `UUID id` (único por documento, R30) | No |
| Titular | `AccountHolder` | Entidad | `UUID id` | Sí, con `rename`; su segmento, solo desde la cuenta |
| Movimiento | `Transaction` | Entidad, hija del agregado | `UUID id` | Solo su estado, una vez y solo desde la cuenta |
| Número de cuenta | `AccountNumber` | Objeto de valor (`record`) | Por valor | No (la cuenta lo sustituye entero, R32) |
| Documento | `TaxId` | Objeto de valor (`record`) | Por valor | No |
| Correo | `EmailAddress` | Objeto de valor (`record`) | Por valor | No |
| Importe | `MonetaryAmount` | Objeto de valor (`record`) | Por valor | No |
| Segmento de cliente | `CustomerSegment` | Enumerado | — | — |
| Tipo de movimiento | `TransactionType` | Enumerado | — | — |
| Estado del movimiento | `TransactionStatus` | Enumerado | — | — |
| Factoría de cuentas | `CheckingAccountFactory` | Factoría | — | Sin estado |
| Transferencia | `MoneyTransferService` | Servicio de dominio | — | Sin estado |
| Revisión del segmento | `SegmentReviewService` | Servicio de dominio | — | Sin estado |

### 4.1 `CheckingAccount` (raíz del agregado)

- **Estado:** `id`, `accountNumber`, `primaryHolder`, `currentBalance`, la lista de `transactions`, la fecha de la próxima revisión del segmento y los eventos de dominio pendientes de publicar.
- **Creación:** solo la factoría (`CheckingAccountFactory`) llama a `CheckingAccount.open(...)`, que es de paquete; el depósito inicial queda registrado como un ingreso aprobado (R1, R2).
- **Comportamiento:**
  - `deposit(amount)` → siempre se aprueba si el importe es positivo y está en la divisa de la cuenta (R3, R4).
  - `tryWithdraw(amount)` → se aprueba si el saldo resultante está permitido; si no, el movimiento queda `REJECTED` y el saldo no cambia (R5-R9).
  - `tryWithdrawForTransfer` / `depositFromTransfer` → lo mismo dentro de una transferencia, guardando la cuenta de contrapartida (R17).
  - `segmentReviewDue(now)` / `resolveSegmentReview(...)` → la revisión del segmento con el resultado de la agencia (R24-R27).
  - `reassignAccountNumber(number)` → solo otro IBAN de Mon-A Bank distinto del actual (R32).
  - `pullDomainEvents()` → entrega y vacía los eventos registrados.
- **Invariantes que protege:** depósito inicial mínimo de 50,00 €, una sola divisa, descubierto solo para `HIGH_NET_WORTH` y hasta -1.000,00 €, y los movimientos no se pueden cambiar desde fuera (`transactions()` devuelve una copia; las factorías de `Transaction` y `approve`/`reject` son de paquete) (R10).

### 4.2 `AccountHolder` (entidad)

Persona a la que pertenece la cuenta: `id`, `fullName`, `taxId` y `segment`. Se crea con `AccountHolder.register(fullName, taxId, segment)`, copiando el nombre y el documento del cliente; el segmento inicial lo deciden sus ingresos verificados (R20). Conserva su identidad aunque cambien sus atributos, y dos titulares con los mismos datos son distintos (R15). El nombre es obligatorio (R21). El segmento decide si se permite el descubierto: sube a alto patrimonio al alcanzar 100.000,00 € de saldo (R22, R23) y, un mes después de caer por debajo, se revisa con los ingresos (R24-R27). Cada cuenta tiene su titular: los saldos de un cliente no se suman.

### 4.3 `Customer` (raíz del agregado)

Cliente de Mon-A Bank: `id`, `taxId`, `fullName` y `email`. Se crea con `Customer.register(...)` en el alta pública o en el alta del administrador. Un documento es de un único cliente (R30); un cliente puede tener varias cuentas. No conoce a los usuarios: la relación con su usuario es un puerto de `application`.

### 4.4 `Transaction` (entidad hija)

Un ingreso o una retirada con `amount`, `occurredOn`, `status` y, si viene de una transferencia, la cuenta de contrapartida. Nace `PENDING` y se resuelve una sola vez a `APPROVED` o `REJECTED` (R11). Solo `CheckingAccount` puede crearlo y resolverlo.

### 4.5 `AccountNumber`, `TaxId`, `EmailAddress` y `MonetaryAmount` (objetos de valor)

- **`AccountNumber`:** un IBAN español, `ES` + 22 dígitos, normalizado sin espacios y en mayúsculas (R14). Los que genera el banco son de Mon-A Bank (entidad `9999`, oficina `0001`, dígitos de control válidos, R31); los que escribe un usuario como destino de una transferencia no se validan.
- **`TaxId`:** un DNI o un NIE con su letra de control comprobada, normalizado en mayúsculas sin depender del idioma del sistema (R28).
- **`EmailAddress`:** el correo del cliente, sin espacios alrededor y en minúsculas (R29).
- **`MonetaryAmount`:** `BigDecimal` + `Currency`, redondeado a los decimales de la divisa (R12). Inmutable: `add`/`subtract` devuelven instancias nuevas; combinar dos divisas lanza `CurrencyMismatchException` (R4, R13).

### 4.6 Eventos de dominio

La cuenta registra lo que le pasa y el servicio de aplicación publica los eventos **después de guardarla**, por el puerto `DomainEventPublisher`. Dos consumidores los reciben: la auditoría (`AuditLogListener`) y el aviso simulado al cliente (`CustomerNotificationListener`), que solo escriben en el log.

| Evento | Cuándo |
| ------ | ------ |
| `CheckingAccountOpened` | Se abre una cuenta (también en el alta de un cliente) |
| `MoneyDeposited` | Ingreso aprobado, incluido el depósito inicial y el abono de una transferencia |
| `MoneyWithdrawn` | Retirada aprobada |
| `WithdrawalRejected` | Retirada rechazada |
| `CustomerSegmentChanged` | El titular sube a alto patrimonio o vuelve a minorista |

El alta de clientes y el cambio de IBAN no tienen eventos propios (decisión del usuario, 2026-11-15).

### 4.7 Errores de dominio

Todos heredan de la clase abstracta `DomainException`; su mensaje en español se muestra tal cual al usuario y se traducen a **HTTP 422**.

| Excepción | Regla |
| --------- | ----- |
| `InsufficientInitialDepositException` | R1 |
| `CurrencyMismatchException` | R4, R19 |
| `DuplicateAccountNumberException` | R16 (red de seguridad: el IBAN lo genera el banco) |
| `DuplicateCustomerException` | R30 |
| `SameAccountTransferException` | R17 |

## 5. Casos de uso

Los adaptadores los piden por los puertos de entrada `CheckingAccountCommands` y `CheckingAccountQueries`, que implementa `CheckingAccountService`. Todos reciben el `Caller`, salvo el alta pública (no tiene sesión) y la revisión diaria (la dispara el reloj). Cada caso de uso se ejecuta en una unidad de trabajo (`UnitOfWork`): nadie ve una cuenta a medio cambiar.

| Caso de uso | Quién | Web | REST | Resultado |
| ----------- | ----- | --- | ---- | --------- |
| Alta de cliente | Visitante | `/login` → `/signup`, `/signup/access`, `/signup/account` | `POST /api/customers` | `AccountSummary` (201) y sesión iniciada |
| Abrir cuenta | Administrador | `GET/POST /accounts/new` | `POST /api/accounts` | `AccountSummary` (201) |
| Abrir otra cuenta | Cliente | `GET/POST /accounts/another` | — | `AccountSummary` |
| Generar nuevo IBAN | Administrador | `POST /accounts/{id}/account-number` | `POST /api/accounts/{id}/account-number` | `AccountSummary` (200) |
| Listar cuentas | Administrador: todas · Usuario: las suyas | `GET /` | `GET /api/accounts` | `List<AccountSummary>` |
| Consultar cuenta (con movimientos) | Administrador / propietario | `GET /accounts/{id}` | `GET /api/accounts/{id}` | `AccountDetails` (con documento y correo del titular) |
| Ingresar | Administrador / propietario | `POST /accounts/{id}/deposits` | `POST /api/accounts/{id}/deposits` | `TransactionSummary` (201) |
| Retirar | Administrador / propietario | `POST /accounts/{id}/withdrawals` | `POST /api/accounts/{id}/withdrawals` | `TransactionSummary` (201, puede ser `REJECTED`) |
| Transferir | Administrador / propietario del origen | `POST /accounts/{id}/transfers` | `POST /api/transfers` | `TransferSummary` (201, puede ser `REJECTED`) |
| Revisar segmentos | Reloj, cada día | — | — | Número de titulares que vuelven a minorista |

Antes de cada retirada y de cada transferencia, el servicio revisa el segmento de la cuenta de origen si le toca, así que el cargo se evalúa ya con el segmento correcto.

### 5.1 Flujo del alta de un cliente

```
SignUpCommand (nombre, documento, correo, usuario, depósito)   [CustomerRegistration]
   │
   ├─ ¿usuario ocupado (en Spring Security o en un cliente/cuenta)? → UsernameTakenException (paso 2, 422)
   └─ CheckingAccountCommands.signUp  (en la unidad de trabajo)
        ├─ construir la cuenta con la factoría ...... depósito o divisa no válidos → 422 (paso 3)
        ├─ ¿el documento ya es de un cliente? ....... DuplicateCustomerException (paso 1, 422)
        ├─ ¿el usuario ya es de otro cliente? ....... IllegalArgumentException (400)
        └─ guardar cliente, usuario del cliente, cuenta y propietario
   crear el usuario USER con la contraseña cifrada → sesión iniciada en la web
```

La unidad de trabajo no deshace cambios: el alta es «todo o nada» porque comprueba todo antes de guardar nada.

### 5.2 Flujo de una transferencia

```
TransferCommand (id de origen, IBAN de destino, importe)
   │
   ├─ revisar el segmento del origen si le toca
   ├─ cargar el origen para el Caller ...... no existe / no es suyo → 404
   ├─ buscar el destino por IBAN ........... no existe → 404
   └─ MoneyTransferService.transfer
        ├─ ¿misma cuenta? .................. SameAccountTransferException (R17, 422)
        ├─ ¿divisa ≠ divisa del destino? ... CurrencyMismatchException (R19, 422)
        ├─ source.tryWithdrawForTransfer(importe)
        └─ ¿aprobada? → target.depositFromTransfer(importe)   (si no, nada cambia, R18)
   save(origen), save(destino) en la misma unidad de trabajo → publicar eventos → TransferSummary
```

Una transferencia se registra como una retirada (`WITHDRAWAL`) en el origen y un ingreso (`DEPOSIT`) en el destino, cada uno con la otra cuenta como contrapartida; la web los muestra como «Transferencia a …» y «Transferencia de …» con el IBAN abreviado.

## 6. Reglas de negocio (resumen)

| Regla | Descripción |
| ----- | ----------- |
| R1 | Una cuenta no se abre con un depósito inicial inferior a 50,00 € |
| R2 | El depósito inicial queda registrado como un ingreso aprobado |
| R3 | Los importes de los ingresos y retiradas deben ser positivos |
| R4 | La cuenta solo opera en la divisa con la que se abrió |
| R5 | Una retirada con saldo suficiente se aprueba |
| R6 | Un cliente minorista no puede dejar la cuenta en descubierto |
| R7 | Un cliente de alto patrimonio puede dejar la cuenta en descubierto |
| R8 | Nadie puede bajar del límite de descubierto de -1.000,00 € |
| R9 | Una retirada rechazada no cambia el saldo |
| R10 | Los movimientos no pueden manipularse desde fuera del agregado |
| R11 | Un movimiento ya resuelto no puede volver a resolverse |
| R12 | Los importes se normalizan a los decimales de su divisa; mismo valor y divisa = importes iguales |
| R13 | Los importes son inmutables y siempre tienen divisa |
| R14 | El número de cuenta es un IBAN español normalizado |
| R15 | Un titular conserva su identidad aunque cambien sus atributos |
| R16 | Los números de cuenta son únicos (red de seguridad: los genera el banco) |
| R17 | Una transferencia a la misma cuenta se rechaza sin registrar movimientos |
| R18 | Si la retirada en origen se rechaza, no se ingresa nada en el destino |
| R19 | Una transferencia en una divisa distinta a la de las cuentas se rechaza antes de retirar |
| R20 | Al abrir, es de alto patrimonio quien tiene ingresos verificados por encima de 100.000,00 € |
| R21 | El nombre del titular es obligatorio |
| R22 | Un abono aprobado que lleva el saldo a 100.000,00 € convierte al titular en alto patrimonio |
| R23 | Una cuenta abierta con el patrimonio mínimo es de alto patrimonio desde el principio |
| R24 | Un mes después de caer bajo el patrimonio mínimo se revisan los ingresos: si no son altos, vuelve a minorista |
| R25 | Recuperar el patrimonio mínimo antes de la revisión la cancela |
| R26 | Con ingresos altos en la revisión conserva el segmento y se revisa cada seis meses |
| R27 | Quien abre como alto patrimonio por ingresos con saldo bajo el mínimo se revisa a los seis meses |
| R28 | El documento es un DNI o un NIE con la letra de control correcta |
| R29 | El correo del cliente tiene un formato válido y se guarda en minúsculas |
| R30 | Un documento es de un único cliente y un usuario también; un cliente puede tener varias cuentas |
| R31 | Todo IBAN lo asigna Mon-A Bank y no se repite |
| R32 | El IBAN solo cambia por otro de Mon-A Bank, y solo lo cambia un administrador |

El test que demuestra cada regla está en el [glosario](../dominio/glosario.md#business-rules).

## 7. Errores que ve el usuario

| Situación | REST | Web |
| --------- | ---- | --- |
| Regla de negocio incumplida (`DomainException`) | 422 `ProblemDetail` | Mensaje en el formulario |
| Recurso inexistente o ajeno (`NotFoundException`) | 404 | Página 404 |
| Entrada no válida (validación, `IllegalArgumentException`) | 400 con `errors` por campo | Errores de campo en el formulario |
| Falta el rol o no es cliente (`ForbiddenOperationException`, `NotACustomerException`) | 403 | Página 403 |
| Sin autenticar | 401 | Redirección a `/login` |
| Alta con un documento que ya es de un cliente | 422 «Ya eres cliente de Mon-A Bank…» | Vuelta al paso 1 con el mensaje y el enlace para entrar |
| Alta con un usuario ocupado | 422 | Vuelta al paso 2 |
| Retirada o transferencia rechazada | **No es un error**: 201 con estado `REJECTED` | Mensaje de aviso |

## 8. Limitaciones funcionales conocidas

- En la práctica solo se pueden abrir cuentas en euros (el depósito mínimo y el límite de descubierto están en EUR).
- Todo vive en memoria (cuentas, clientes, usuarios del alta y fechas de revisión) y se pierde al reiniciar; no hay verificación del correo, ni recuperación o cambio de contraseña, ni límite de altas o de intentos de acceso (ADR 0008, ADR 0012).
- «Abrir otra cuenta» solo existe en la web.
- Los consumidores de eventos son síncronos: si uno fallara, el error llegaría al usuario con la cuenta ya guardada.
- Si la agencia tributaria fallara en una revisión, se abortaría el cargo que la provocó (la simulada nunca falla).
- Un usuario puede averiguar si un IBAN existe intentando una transferencia (riesgo aceptado en el ADR 0008).

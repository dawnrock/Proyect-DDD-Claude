# Análisis funcional

> Fotografía de la rama `Monolithic` (fase 1, monolito en capas) después de la PR #51 (login con roles). La fuente de verdad de los términos y las reglas es el [glosario](../dominio/glosario.md); este documento los agrupa por dominio y explica cómo encajan las piezas.

## 1. Propósito de la aplicación

Una pequeña **aplicación bancaria de cuentas corrientes**. Un administrador abre cuentas para los clientes; los clientes inician sesión, consultan su saldo y sus movimientos, ingresan, retiran y transfieren dinero a otras cuentas. Las reglas de negocio (depósito inicial mínimo, descubierto según el segmento del cliente, una sola divisa por cuenta…) viven en un modelo de dominio en Java puro construido con los patrones tácticos de DDD.

Dos canales exponen los mismos casos de uso:

- **Web** (Thymeleaf, renderizada en el servidor), para personas.
- **API REST** (`/api/**`, documentada en [`openapi.yaml`](../api/openapi.yaml)), para máquinas.

## 2. Actores

| Actor | Rol | Qué puede hacer |
| ----- | --- | --------------- |
| Administrador | `ADMIN` | Ve todas las cuentas, abre cuentas y las asigna a un usuario, opera sobre cualquier cuenta y usa la consola H2. |
| Cliente (usuario) | `USER` | Ve y opera **solo sus** cuentas; puede transferir a cualquier cuenta existente indicando su IBAN. |
| Sistema | `Caller.system()` interno | Carga los datos de demostración al arrancar (perfil `demo`). |

Un cliente que pide una cuenta que no es suya recibe un **404** (no un 403), para no revelar que existen las cuentas de otras personas.

## 3. Dominios (áreas delimitadas)

La aplicación tiene un dominio principal y dos áreas de apoyo. Solo el dominio principal se modela con DDD; las otras se mantienen fuera de `domain` a propósito.

```
┌──────────────────────────────────────────────────────────────┐
│ Dominio principal: Cuentas corrientes   (domain.model)        │
│   CheckingAccount ─┬─ AccountHolder ── CustomerSegment        │
│                    ├─ AccountNumber                           │
│                    ├─ MonetaryAmount                          │
│                    └─ Transaction ─┬─ TransactionType         │
│                                    └─ TransactionStatus       │
│ Servicio de dominio: Transferencias      (domain.service)     │
│   MoneyTransferService                                        │
└──────────────────────────────────────────────────────────────┘
┌───────────────────────────────┐ ┌────────────────────────────┐
│ Apoyo: Identidad y acceso     │ │ Apoyo: Datos de demostración│
│ (presentation + application)  │ │ (application, perfil demo) │
│ usuario, roles, Caller,       │ │ DemoDataLoader             │
│ propietario de la cuenta      │ │                            │
└───────────────────────────────┘ └────────────────────────────┘
```

### 3.1 Dominio principal · Cuentas corrientes

Todo lo relativo al dinero, los saldos y las reglas que los protegen. No sabe nada de usuarios, HTTP, Spring ni persistencia.

### 3.2 Servicio de dominio · Transferencias

Una operación que implica **dos** agregados y que, por tanto, no pertenece a ninguno de ellos. `MoneyTransferService` es Java puro y sin estado: coordina dos `CheckingAccount` sin añadir reglas de saldo propias.

### 3.3 Área de apoyo · Identidad y acceso

Quién usa la aplicación y de quién es cada cuenta. **No forma parte del dominio de cuentas** (ADR 0008):

- La autenticación y los roles los gestiona Spring Security en `presentation`.
- El `Caller` (usuario + indicador de administrador) viaja hasta el servicio de aplicación.
- El propietario de cada cuenta (id de cuenta → usuario) lo guarda `InMemoryAccountOwnershipRepository` en `persistence`, fuera del agregado.

### 3.4 Área de apoyo · Datos de demostración

Con el perfil `demo` (el que usa por defecto `./mvnw spring-boot:run`), `DemoDataLoader` abre tres cuentas mediante los casos de uso normales, así que cumplen las mismas reglas:

| IBAN | Titular | Segmento | Depósito inicial | Propietario |
| ---- | ------- | -------- | ---------------- | ----------- |
| ES9121000418450200051332 | Ada Lovelace | RETAIL | 1.000,00 € | `ada` |
| ES7921000813610123456789 | Grace Hopper | HIGH_NET_WORTH | 5.000,00 € | `grace` |
| ES6000491500051234567892 | Alan Turing | RETAIL | 250,00 € | `alan` |

## 4. Modelo de dominio: entidades y objetos de valor

| Concepto | Clase | Tipo DDD | Identidad | ¿Cambia? |
| -------- | ----- | -------- | --------- | -------- |
| Cuenta corriente | `CheckingAccount` | **Raíz del agregado** | `UUID id` | Sí, solo con `deposit` / `tryWithdraw` |
| Titular | `AccountHolder` | Entidad | `UUID id` | Sí, con `rename` / `promoteToHighNetWorth` |
| Movimiento | `Transaction` | Entidad, hija del agregado | `UUID id` | Solo su estado, una vez y solo desde la cuenta |
| Número de cuenta | `AccountNumber` | Objeto de valor (`record`) | Por valor | No |
| Importe | `MonetaryAmount` | Objeto de valor (`record`) | Por valor | No |
| Segmento de cliente | `CustomerSegment` | Enumerado | — | — |
| Tipo de movimiento | `TransactionType` | Enumerado | — | — |
| Estado del movimiento | `TransactionStatus` | Enumerado | — | — |
| Transferencia | `MoneyTransferService` | Servicio de dominio | — | Sin estado |

### 4.1 `CheckingAccount` (raíz del agregado)

- **Estado:** `id`, `accountNumber`, `primaryHolder`, `currentBalance` y la lista de `transactions`.
- **Creación:** `CheckingAccount.open(accountNumber, holder, initialDeposit)`, con constructor privado; el depósito inicial queda registrado como un ingreso aprobado (R1, R2).
- **Comportamiento:**
  - `deposit(amount)` → siempre se aprueba si el importe es positivo y está en la divisa de la cuenta (R3, R4).
  - `tryWithdraw(amount)` → se aprueba si el saldo resultante está permitido; si no, el movimiento queda `REJECTED` y el saldo no cambia (R5-R9).
- **Invariantes que protege:** depósito inicial mínimo de 50,00 €, una sola divisa, descubierto solo para `HIGH_NET_WORTH` y hasta -1.000,00 €, y los movimientos no se pueden cambiar desde fuera (`transactions()` devuelve una copia; las factorías de `Transaction` y `approve`/`reject` son de paquete) (R10).

### 4.2 `AccountHolder` (entidad)

Persona a la que pertenece la cuenta: `id`, `fullName` y `segment`. Se crea con `AccountHolder.register(...)`. Conserva su identidad aunque cambien sus atributos, y dos titulares con los mismos datos son distintos (R15). El segmento decide si se permite el descubierto.

### 4.3 `Transaction` (entidad hija)

Un ingreso o una retirada con `amount`, `occurredOn` y `status`. Nace `PENDING` y se resuelve una sola vez a `APPROVED` o `REJECTED` (R11). Solo `CheckingAccount` puede crearlo y resolverlo.

### 4.4 `AccountNumber` (objeto de valor)

Un IBAN español: `ES` + 22 dígitos, normalizado sin espacios y en mayúsculas (R14). No se valida el dígito de control.

### 4.5 `MonetaryAmount` (objeto de valor)

`BigDecimal` + `Currency`, redondeado a los decimales de la divisa (R12). Inmutable: `add`/`subtract` devuelven instancias nuevas; combinar dos divisas lanza `CurrencyMismatchException` (R4, R13).

### 4.6 Errores de dominio

Todos heredan de la clase abstracta `DomainException`; su mensaje en español se muestra tal cual al usuario y se traducen a **HTTP 422**.

| Excepción | Regla |
| --------- | ----- |
| `InsufficientInitialDepositException` | R1 |
| `CurrencyMismatchException` | R4, R19 |
| `DuplicateAccountNumberException` | R16 |
| `SameAccountTransferException` | R17 |

## 5. Casos de uso

Todos viven en `CheckingAccountService` y reciben el `Caller`.

| Caso de uso | Quién | Web | REST | Resultado |
| ----------- | ----- | --- | ---- | --------- |
| Abrir cuenta | Administrador | `GET/POST /accounts/new` | `POST /api/accounts` | `AccountSummary` (201) |
| Listar cuentas | Administrador: todas · Usuario: las suyas | `GET /` | `GET /api/accounts` | `List<AccountSummary>` |
| Consultar cuenta (con movimientos) | Administrador / propietario | `GET /accounts/{id}` | `GET /api/accounts/{id}` | `AccountDetails` |
| Ingresar | Administrador / propietario | `POST /accounts/{id}/deposits` | `POST /api/accounts/{id}/deposits` | `TransactionSummary` (201) |
| Retirar | Administrador / propietario | `POST /accounts/{id}/withdrawals` | `POST /api/accounts/{id}/withdrawals` | `TransactionSummary` (201, puede ser `REJECTED`) |
| Transferir | Administrador / propietario del origen | `POST /accounts/{id}/transfers` | `POST /api/transfers` | `TransferSummary` (201, puede ser `REJECTED`) |

### 5.1 Flujo de una transferencia

```
TransferCommand (id de origen, IBAN de destino, importe)
   │
   ├─ cargar el origen para el Caller ...... no existe / no es suyo → 404
   ├─ buscar el destino por IBAN ........... no existe → 404
   └─ MoneyTransferService.transfer
        ├─ ¿misma cuenta? .................. SameAccountTransferException (R17, 422)
        ├─ ¿divisa ≠ divisa del destino? ... CurrencyMismatchException (R19, 422)
        ├─ source.tryWithdraw(importe)
        └─ ¿aprobada? → target.deposit(importe)   (si no, nada cambia, R18)
   save(origen), save(destino) → TransferSummary(estado APPROVED | REJECTED)
```

Una transferencia se registra como una retirada (`WITHDRAWAL`) en el origen y un ingreso (`DEPOSIT`) en el destino; no hay un tipo de movimiento propio.

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
| R16 | Los números de cuenta son únicos (lo comprueba el caso de uso "abrir cuenta") |
| R17 | Una transferencia a la misma cuenta se rechaza sin registrar movimientos |
| R18 | Si la retirada en origen se rechaza, no se ingresa nada en el destino |
| R19 | Una transferencia en una divisa distinta a la de las cuentas se rechaza antes de retirar |

El test que demuestra cada regla está en el [glosario](../dominio/glosario.md#reglas-de-negocio).

## 7. Errores que ve el usuario

| Situación | REST | Web |
| --------- | ---- | --- |
| Regla de negocio incumplida (`DomainException`) | 422 `ProblemDetail` | Mensaje en el formulario |
| Recurso inexistente o ajeno (`NotFoundException`) | 404 | Página 404 |
| Entrada no válida (validación, `IllegalArgumentException`) | 400 con `errors` por campo | Errores de campo en el formulario |
| Falta el rol (`ForbiddenOperationException`) | 403 | Página 403 |
| Sin autenticar | 401 | Redirección a `/login` |
| Retirada o transferencia rechazada | **No es un error**: 201 con estado `REJECTED` | Mensaje de aviso |

## 8. Limitaciones funcionales conocidas

- En la práctica solo se pueden abrir cuentas en euros (el depósito mínimo y el límite de descubierto están en EUR).
- Los usuarios son estáticos (usuarios de demostración o un administrador por variables de entorno); no hay alta ni cambio de contraseña.
- Dos transferencias simultáneas sobre la misma cuenta podrían perder una actualización (dos `save` sin transacción; se acepta con el repositorio en memoria y se arreglará en la fase 2).
- Un usuario puede averiguar si un IBAN existe intentando una transferencia (riesgo aceptado en el ADR 0008).
- El listado de cuentas no muestra el segmento; las transferencias aparecen como una retirada y un ingreso.

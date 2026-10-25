# Glosario (lenguaje ubicuo)

Términos del dominio de cuentas corrientes. El código usa el nombre en inglés; la documentación, los mensajes de error y los `@DisplayName`, el término en español. Si aparece un término nuevo, se añade aquí en la misma PR.

## Términos del dominio actual (`domain.model`)

| Término | En el código | Tipo DDD | Definición |
| ------- | ------------ | -------- | ---------- |
| Cuenta corriente | `CheckingAccount` | Agregado (raíz) | Cuenta de un titular en una única divisa, con saldo y lista de movimientos. Es el único punto de entrada para operar: protege sus invariantes (R1-R11). Se abre con `CheckingAccount.open(...)`. |
| Titular | `AccountHolder` | Entidad | Persona a nombre de la que está la cuenta. Tiene identidad propia (`id`), nombre completo y segmento. Se da de alta con `AccountHolder.register(...)` y cambia con `rename` y `promoteToHighNetWorth`. |
| Segmento de cliente | `CustomerSegment` | Enumerado | Clasificación comercial del titular: `RETAIL` (minorista) o `HIGH_NET_WORTH` (alto patrimonio). Determina si puede quedar en descubierto. |
| Número de cuenta | `AccountNumber` | Objeto de valor | IBAN español que identifica la cuenta (R14). |
| Importe | `MonetaryAmount` | Objeto de valor | Cantidad y divisa. Inmutable y normalizado a los decimales de su divisa (R12, R13). Se crea con `MonetaryAmount.euros("100.00")`. |
| Movimiento | `Transaction` | Entidad (hija del agregado) | Ingreso o retirada sobre una cuenta, con importe, instante (`occurredOn`) y estado. Solo lo crea y lo resuelve la cuenta. |
| Tipo de movimiento | `TransactionType` | Enumerado | `DEPOSIT` (ingreso) o `WITHDRAWAL` (retirada). |
| Estado del movimiento | `TransactionStatus` | Enumerado | `PENDING` (pendiente, al crearse), `APPROVED` (aprobado) o `REJECTED` (rechazado). Un movimiento se resuelve una sola vez (R11). |
| Ingreso | `deposit` | Operación de la cuenta | Suma el importe al saldo. Siempre se aprueba si el importe es válido (R3, R4). |
| Retirada | `tryWithdraw` | Operación de la cuenta | Resta el importe si el saldo resultante es válido; si no, el movimiento queda rechazado y el saldo no cambia (R5-R9). En el código, `WITHDRAWAL` y `WithdrawCommand`. |
| Depósito inicial mínimo | `MINIMUM_INITIAL_DEPOSIT` | Regla | 50,00 € (R1). |
| Descubierto | `OVERDRAFT_LIMIT` | Regla | Saldo negativo permitido solo a alto patrimonio, con un límite de -1.000,00 € (R6-R8). |
| Error de dominio | `DomainException` | Excepción de dominio (abstracta) | Base de los errores de negocio. Su mensaje, en español, se muestra tal cual al usuario. |
| Depósito inicial insuficiente | `InsufficientInitialDepositException` | Excepción de dominio | La cuenta se intenta abrir por debajo del depósito mínimo (R1). |
| Divisa distinta | `CurrencyMismatchException` | Excepción de dominio | Se combinan importes de divisas distintas o se opera en una divisa distinta a la de la cuenta (R4). |
| Número de cuenta duplicado | `DuplicateAccountNumberException` | Excepción de dominio | Se intenta abrir una cuenta con un número que ya usa otra (R16). La lanza el caso de uso "abrir cuenta", que es quien consulta el repositorio. |
| Transferencia a la misma cuenta | `SameAccountTransferException` | Excepción de dominio | Se intenta transferir de una cuenta a sí misma (R17). La lanza `MoneyTransferService` antes de registrar ningún movimiento. |

## Servicios de dominio (`domain.service`)

| Término | En el código | Tipo DDD | Definición |
| ------- | ------------ | -------- | ---------- |
| Transferencia | `MoneyTransferService.transfer` | Servicio de dominio (sin estado, Java puro) | Mueve un importe de una cuenta de origen a una de destino. Comprueba la misma cuenta (R17) y la divisa del destino (R19), intenta la retirada en origen y **solo si se aprueba** ingresa en destino (R18). Devuelve la retirada del origen, cuyo estado es el resultado de la transferencia. Se registra como una retirada (`WITHDRAWAL`) en origen y un ingreso (`DEPOSIT`) en destino; no hay tipos de movimiento propios. |

**Transferencias simultáneas:** el caso de uso guarda las dos cuentas con dos `save` sin transacción ni bloqueo, así que dos transferencias simultáneas sobre la misma cuenta podrían perder una actualización. Se acepta en un repositorio en memoria de práctica (como la carrera de R16); en la fase 2, con persistencia real, irá en una transacción.

Los argumentos nulos o inválidos que no son reglas de negocio (por ejemplo, un importe no positivo o un IBAN mal formado) se rechazan con `IllegalArgumentException` o `NullPointerException`.

## Reglas de negocio

Cada regla cita el test que la demuestra (`src/test/java/com/pronoide/ddd/domain/model/` y, para R17-R19, `domain/service/`).

| Regla | Descripción | Test |
| ----- | ----------- | ---- |
| R1 | Una cuenta no se abre con un depósito inicial inferior a 50,00 € | `CheckingAccountTests#una_cuenta_no_puede_abrirse_por_debajo_del_deposito_minimo` |
| R2 | Al abrir la cuenta, el depósito inicial queda registrado como un ingreso aprobado | `CheckingAccountTests#al_abrir_la_cuenta_el_deposito_inicial_queda_registrado` |
| R3 | Los importes de los ingresos y retiradas deben ser positivos | `CheckingAccountTests#los_importes_de_las_operaciones_deben_ser_positivos` |
| R4 | La cuenta solo opera en la divisa con la que se abrió, y no se combinan importes de divisas distintas | `CheckingAccountTests#la_cuenta_solo_opera_en_la_divisa_con_la_que_se_abrio`, `MonetaryAmountTests#no_se_pueden_combinar_importes_de_divisas_distintas` |
| R5 | Una retirada con saldo suficiente se aprueba | `CheckingAccountTests#una_retirada_con_saldo_suficiente_se_aprueba` |
| R6 | Un cliente minorista no puede dejar la cuenta en descubierto | `CheckingAccountTests#un_cliente_minorista_no_puede_dejar_la_cuenta_en_descubierto` |
| R7 | Un cliente de alto patrimonio puede dejar la cuenta en descubierto | `CheckingAccountTests#un_cliente_de_alto_patrimonio_puede_dejar_la_cuenta_en_descubierto` |
| R8 | Ningún cliente puede superar el límite de descubierto de -1.000,00 € | `CheckingAccountTests#ningun_cliente_puede_superar_el_limite_de_descubierto`, `CheckingAccountTests#las_retiradas_en_el_limite_del_descubierto_dependen_del_segmento` |
| R9 | Una retirada rechazada no cambia el saldo | `CheckingAccountTests#ningun_cliente_puede_superar_el_limite_de_descubierto` |
| R10 | Los movimientos no pueden manipularse desde fuera del agregado | `CheckingAccountTests#los_movimientos_no_pueden_manipularse_desde_fuera_del_agregado` |
| R11 | Un movimiento ya resuelto no puede volver a resolverse | `CheckingAccountTests#un_movimiento_ya_resuelto_no_puede_volver_a_resolverse` |
| R12 | Un importe se normaliza a los decimales de su divisa, y dos importes con el mismo valor y divisa son iguales | `MonetaryAmountTests#el_importe_se_normaliza_a_los_decimales_de_su_divisa`, `MonetaryAmountTests#dos_importes_con_el_mismo_valor_y_divisa_son_equivalentes` |
| R13 | Un importe es inmutable: las operaciones devuelven instancias nuevas; no existe sin divisa | `MonetaryAmountTests#las_operaciones_devuelven_nuevas_instancias`, `MonetaryAmountTests#un_importe_no_puede_construirse_sin_divisa` |
| R14 | El número de cuenta es un IBAN español: `ES` seguido de 22 dígitos, normalizado sin espacios y en mayúsculas. **No se valida el dígito de control.** | `AccountNumberTests#el_numero_de_cuenta_se_normaliza`, `AccountNumberTests#un_iban_con_formato_incorrecto_no_llega_a_construirse` |
| R15 | Un titular conserva su identidad aunque cambien sus atributos, y dos titulares con los mismos datos son distintos | `AccountHolderTests#un_titular_mantiene_su_identidad_aunque_cambien_sus_atributos`, `AccountHolderTests#dos_titulares_con_los_mismos_datos_son_entidades_distintas` |
| R16 | No se puede abrir una cuenta con un número que ya usa otra, aunque venga con espacios o en minúsculas | `CheckingAccountServiceTests#un_numero_de_cuenta_en_uso_se_rechaza_aunque_venga_con_espacios_o_en_minusculas`, `DuplicateAccountNumberExceptionTests#un_numero_de_cuenta_duplicado_es_una_regla_de_negocio_incumplida` |
| R17 | No se puede transferir a la misma cuenta; se rechaza sin registrar movimientos | `MoneyTransferServiceTests#transferir_a_la_misma_cuenta_se_rechaza_sin_registrar_movimientos` |
| R18 | Si la retirada en origen se rechaza, no se ingresa nada en destino y ningún saldo cambia | `MoneyTransferServiceTests#si_la_retirada_se_rechaza_no_se_ingresa_nada_en_el_destino` |
| R19 | Una transferencia en una divisa distinta a la de las cuentas se rechaza antes de retirar, sin registrar movimientos | `MoneyTransferServiceTests#un_importe_en_otra_divisa_se_rechaza_sin_registrar_movimientos` (hoy la retirada en origen ya lo impide, porque todas las cuentas son en euros; la comprobación del destino protege el caso de cuentas en otra divisa) |

### Reglas implementadas sin test

- El nombre del titular es obligatorio y no puede estar vacío (`AccountHolder.register` y `rename`). Falta su test: se añadirá en la primera issue que toque `AccountHolder`.

## Persistencia (`persistence`)

| Término | En el código | Tipo | Definición |
| ------- | ------------ | ---- | ---------- |
| Repositorio de cuentas | `InMemoryCheckingAccountRepository` | Repositorio del agregado (en memoria, simulado) | Guarda y recupera cuentas corrientes completas, con sus movimientos: `save` (guarda o sustituye por id), `findById`, `findByAccountNumber` y `findAll` (ordenadas por número de cuenta, lista no modificable). **El número de cuenta es único**: guardar otra cuenta con un número ya usado lanza `IllegalStateException`, como un índice único en una base de datos. Guarda referencias, no copias, así que los casos de uso deben llamar siempre a `save` después de modificar una cuenta. Tests: `InMemoryCheckingAccountRepositoryTests`. |

**Número de cuenta único como regla de negocio:** el repositorio lo vigila como una restricción técnica (`IllegalStateException`, que no es una `DomainException`). El caso de uso "abrir cuenta" (#5) lo comprueba antes con `findByAccountNumber` y lanza `DuplicateAccountNumberException` (R16), que la presentación traduce (D4) a 422 o a un mensaje en el formulario. Limitación conocida: dos altas simultáneas con el mismo número pueden pasar la comprobación, y entonces `save` lanza `IllegalStateException` (500); se acepta en un repositorio en memoria de práctica.

## Aplicación y presentación

| Término | En el código | Tipo | Definición |
| ------- | ------------ | ---- | ---------- |
| Recurso inexistente | `NotFoundException` (`application`) | Error de aplicación (abstracto) | Base de los errores "lo que se pide no existe". No es una regla de negocio: lo decide el caso de uso al consultar la persistencia. La API REST lo traduce a **404** con el título "Recurso inexistente"; sus subclases concretas (por ejemplo, `AccountNotFoundException`, #5) aportan el mensaje. |
| Regla de negocio incumplida | `DomainException` → 422 | Traducción HTTP | Título del `ProblemDetail` con el que la API devuelve cualquier excepción de dominio. |
| Petición no válida | `IllegalArgumentException` y validación → 400 | Traducción HTTP | Título del `ProblemDetail` para formatos, validaciones o argumentos que el dominio rechaza; la validación añade `errors` con un mensaje por campo. |
| Servicio de cuentas | `CheckingAccountService` | Servicio de aplicación | Casos de uso abrir (`openAccount`), consultar (`findAccount`) y listar (`listAccounts`) cuentas, ingresar (`deposit`), retirar (`withdraw`) y transferir (`transfer`). Carga y guarda los agregados; sin reglas de negocio salvo la comprobación de R16 (la transferencia la delega en `MoneyTransferService`). Contratos: `docs/contracts/open-query-list-accounts.md`, `deposits-withdrawals.md` y `money-transfers.md`. |
| Cuenta inexistente | `AccountNotFoundException` (hereda de `NotFoundException`) | Error de aplicación | Se consulta una cuenta con un id que no existe → 404. |
| Abrir cuenta (orden) | `OpenAccountCommand` | Command de aplicación | Número de cuenta, titular, segmento, depósito inicial y divisa (`EUR` por defecto). Valida sus campos al construirse (`IllegalArgumentException`). |
| Resumen y detalle de cuenta | `AccountSummary`, `AccountDetails` | Vistas de aplicación | Lo que la presentación ve de una cuenta, sin exponer el agregado; el detalle incluye el segmento y los movimientos. |
| Movimiento (vista) | `TransactionSummary` | Vista de aplicación | Tipo, importe, divisa, instante y estado de un movimiento. |
| Ingresar / retirar (orden) | `DepositCommand`, `WithdrawCommand` | Commands de aplicación | Cuenta, importe y divisa (`EUR` por defecto) de un ingreso o una retirada. Validan al construirse que el importe sea positivo después de redondearlo a los decimales de la divisa y que la divisa exista (`IllegalArgumentException`). Los usan `CheckingAccountService.deposit` y `withdraw`, que devuelven el movimiento (`TransactionSummary`); una retirada no permitida vuelve como `REJECTED`, no como error. |
| Transferir (orden) | `TransferCommand` | Command de aplicación | Cuenta de origen, cuenta de destino, importe y divisa (`EUR` por defecto). Valida el formato como `DepositCommand`; que origen y destino coincidan es una regla de negocio (R17), no de formato. |
| Transferencia (vista) | `TransferSummary` | Vista de aplicación | Ids y números de las dos cuentas, importe e instante de la retirada en origen y estado (`APPROVED` o `REJECTED`). Una transferencia no permitida vuelve como `REJECTED`, no como error. |

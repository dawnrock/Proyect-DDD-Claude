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
| Retirada (reintegro) | `tryWithdraw` | Operación de la cuenta | Resta el importe si el saldo resultante es válido; si no, el movimiento queda rechazado y el saldo no cambia (R5-R9). |
| Depósito inicial mínimo | `MINIMUM_INITIAL_DEPOSIT` | Regla | 50,00 € (R1). |
| Descubierto | `OVERDRAFT_LIMIT` | Regla | Saldo negativo permitido solo a alto patrimonio, con un límite de -1.000,00 € (R6-R8). |
| Error de dominio | `DomainException` | Excepción de dominio (abstracta) | Base de los errores de negocio. Su mensaje, en español, se muestra tal cual al usuario. |
| Depósito inicial insuficiente | `InsufficientInitialDepositException` | Excepción de dominio | La cuenta se intenta abrir por debajo del depósito mínimo (R1). |
| Divisa distinta | `CurrencyMismatchException` | Excepción de dominio | Se combinan importes de divisas distintas o se opera en una divisa distinta a la de la cuenta (R4). |

Los argumentos nulos o inválidos que no son reglas de negocio (por ejemplo, un importe no positivo o un IBAN mal formado) se rechazan con `IllegalArgumentException` o `NullPointerException`.

## Reglas de negocio

Cada regla cita el test que la demuestra (`src/test/java/com/pronoide/ddd/domain/model/`).

| Regla | Descripción | Test |
| ----- | ----------- | ---- |
| R1 | Una cuenta no se abre con un depósito inicial inferior a 50,00 € | `CheckingAccountTests#una_cuenta_no_puede_abrirse_por_debajo_del_deposito_minimo` |
| R2 | Al abrir la cuenta, el depósito inicial queda registrado como un ingreso aprobado | `CheckingAccountTests#al_abrir_la_cuenta_el_deposito_inicial_queda_registrado` |
| R3 | Los importes de los ingresos y retiradas deben ser positivos | `CheckingAccountTests#los_importes_de_las_operaciones_deben_ser_positivos` |
| R4 | La cuenta solo opera en la divisa con la que se abrió, y no se combinan importes de divisas distintas | `CheckingAccountTests#la_cuenta_solo_opera_en_la_divisa_con_la_que_se_abrio`, `MonetaryAmountTests#no_se_pueden_combinar_importes_de_divisas_distintas` |
| R5 | Una retirada con saldo suficiente se aprueba | `CheckingAccountTests#un_reintegro_con_saldo_suficiente_se_aprueba` |
| R6 | Un cliente minorista no puede dejar la cuenta en descubierto | `CheckingAccountTests#un_cliente_minorista_no_puede_dejar_la_cuenta_en_descubierto` |
| R7 | Un cliente de alto patrimonio puede dejar la cuenta en descubierto | `CheckingAccountTests#un_cliente_de_alto_patrimonio_puede_dejar_la_cuenta_en_descubierto` |
| R8 | Ningún cliente puede superar el límite de descubierto de -1.000,00 € | `CheckingAccountTests#ningun_cliente_puede_superar_el_limite_de_descubierto` |
| R9 | Una retirada rechazada no cambia el saldo | `CheckingAccountTests#ningun_cliente_puede_superar_el_limite_de_descubierto` |
| R10 | Los movimientos no pueden manipularse desde fuera del agregado | `CheckingAccountTests#los_movimientos_no_pueden_manipularse_desde_fuera_del_agregado` |
| R11 | Un movimiento ya resuelto no puede volver a resolverse | `CheckingAccountTests#una_transaccion_ya_resuelta_no_puede_volver_a_resolverse` |
| R12 | Un importe se normaliza a los decimales de su divisa, y dos importes con el mismo valor y divisa son iguales | `MonetaryAmountTests#el_importe_se_normaliza_a_los_decimales_de_su_divisa`, `MonetaryAmountTests#dos_importes_con_el_mismo_valor_y_divisa_son_equivalentes` |
| R13 | Un importe es inmutable: las operaciones devuelven instancias nuevas; no existe sin divisa | `MonetaryAmountTests#las_operaciones_devuelven_nuevas_instancias`, `MonetaryAmountTests#un_importe_no_puede_construirse_sin_divisa` |
| R14 | El número de cuenta es un IBAN español: `ES` seguido de 22 dígitos, normalizado sin espacios y en mayúsculas. **No se valida el dígito de control.** | `AccountNumberTests#el_numero_de_cuenta_se_normaliza`, `AccountNumberTests#un_iban_con_formato_incorrecto_no_llega_a_construirse` |
| R15 | Un titular conserva su identidad aunque cambien sus atributos, y dos titulares con los mismos datos son distintos | `AccountHolderTests#un_titular_mantiene_su_identidad_aunque_cambien_sus_atributos`, `AccountHolderTests#dos_titulares_con_los_mismos_datos_son_entidades_distintas` |

### Reglas implementadas sin test

- El nombre del titular es obligatorio y no puede estar vacío (`AccountHolder.register` y `rename`). Falta su test: se añadirá en la primera issue que toque `AccountHolder`.

## Términos previstos

Llegan con issues posteriores de la fase 1; se pasan a la tabla principal cuando existan en el código.

| Término | En el código | Tipo | Issue |
| ------- | ------------ | ---- | ----- |
| Repositorio de cuentas | `InMemoryCheckingAccountRepository` | Persistencia (simulada) | #4 |
| Cuenta inexistente | `AccountNotFoundException` | Error de aplicación | #5 |
| Abrir cuenta (orden) | `OpenAccountCommand` | Command de aplicación | #5 |
| Resumen y detalle de cuenta | `AccountSummary`, `AccountDetails` | Vistas de aplicación | #5 |
| Ingreso / retirada (orden) | `DepositCommand`, `WithdrawCommand` | Commands de aplicación | #6 |
| Transferencia | `MoneyTransferService`, `TransferCommand` | Servicio de dominio y command | #7 |

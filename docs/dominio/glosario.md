# Glosario (lenguaje ubicuo)

Términos del dominio de cuentas corrientes. El código usa el nombre en inglés; la documentación y los mensajes, el término en español. Se completa en la issue "Documentar el glosario y la arquitectura en capas de la fase 1".

| Término | En el código | Tipo DDD | Definición y reglas |
| ------- | ------------ | -------- | ------------------- |
| Cuenta corriente | `CheckingAccount` | Agregado (raíz) | Cuenta de un titular en una única divisa. Se abre con un depósito inicial mínimo de 50,00 €. Solo un cliente de alto patrimonio puede quedar en descubierto, hasta -1.000,00 €. |
| Titular | `AccountHolder` | Entidad | Persona a nombre de la que está la cuenta. Tiene nombre completo obligatorio y un segmento. |
| Segmento de cliente | `CustomerSegment` | Enumerado | `RETAIL` (minorista) o `HIGH_NET_WORTH` (alto patrimonio). |
| Número de cuenta | `AccountNumber` | Objeto de valor | IBAN que identifica la cuenta. |
| Importe | `MonetaryAmount` | Objeto de valor | Cantidad y divisa, normalizada a los decimales de la divisa. Solo opera con importes de la misma divisa. |
| Movimiento | `Transaction` | Entidad (hija del agregado) | Ingreso o retirada sobre la cuenta. Nace pendiente y se resuelve una sola vez como aprobado o rechazado. |
| Ingreso | `deposit` / `TransactionType.DEPOSIT` | Operación | Siempre se aprueba si el importe es positivo y de la divisa de la cuenta. |
| Retirada | `tryWithdraw` / `TransactionType.WITHDRAWAL` | Operación | Se aprueba si el saldo resultante es válido; si no, queda rechazada sin cambiar el saldo. |
| Descubierto | `OVERDRAFT_LIMIT` | Regla | Saldo negativo permitido solo a alto patrimonio, con límite de -1.000,00 €. |
| Transferencia | `MoneyTransferService` (fase 1) | Servicio de dominio | Retirada en origen e ingreso en destino solo si la retirada se aprueba. |

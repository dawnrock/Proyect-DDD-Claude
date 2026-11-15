package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.immutables.value.Value;

/** Detalle de una cuenta, con sus movimientos en el orden en que se registraron. */
@ApplicationValue
@Value.Immutable
public interface AccountDetailsDef {

    UUID id();

    String accountNumber();

    String holderName();

    /** Documento (DNI o NIE) normalizado del titular. Vacío por defecto: el servicio lo rellena siempre. */
    @Value.Default
    default String holderTaxId() {
        return "";
    }

    /** Correo del cliente titular; vacío si la cuenta no es de un cliente registrado. */
    @Value.Default
    default String holderEmail() {
        return "";
    }

    /** {@code RETAIL} o {@code HIGH_NET_WORTH}. */
    String segment();

    BigDecimal balance();

    String currency();

    List<TransactionSummary> transactions();
}

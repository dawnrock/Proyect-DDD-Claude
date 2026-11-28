package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Un movimiento de la cuenta, tal como lo ve la presentación.
 *
 * @param type                     {@code DEPOSIT} o {@code WITHDRAWAL}
 * @param status                   {@code PENDING}, {@code APPROVED} o {@code REJECTED}
 * @param counterpartAccountNumber IBAN normalizado de la otra cuenta si el movimiento forma parte de una transferencia
 *                                 (también si se rechazó): la de destino en la retirada del origen y la de origen en el
 *                                 ingreso del destino. Vacío en los demás (#72)
 */
public record TransactionSummary(UUID id, String type, BigDecimal amount, String currency, Instant occurredOn,
                                 String status, Optional<String> counterpartAccountNumber) {

    public TransactionSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(counterpartAccountNumber, "counterpartAccountNumber");
    }

    /** Un movimiento que no es de una transferencia: sin cuenta de contrapartida. */
    public static TransactionSummary of(UUID id, String type, BigDecimal amount, String currency, Instant occurredOn,
                                        String status) {
        return new TransactionSummary(id, type, amount, currency, occurredOn, status, Optional.empty());
    }

    /** Si el movimiento se aprobó. Los adaptadores lo usan en vez de comparar el texto del estado (#82). */
    public boolean approved() {
        return "APPROVED".equals(status);
    }

    /** Si el movimiento se rechazó. */
    public boolean rejected() {
        return "REJECTED".equals(status);
    }
}

package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de una transferencia, tal como lo ve la presentación.
 *
 * @param amount     importe de la retirada en origen, ya redondeado a los decimales de la divisa
 * @param occurredOn instante de la retirada en origen
 * @param status     {@code APPROVED} o {@code REJECTED}: el estado de la retirada en la cuenta de origen
 */
public record TransferSummary(UUID sourceAccountId, String sourceAccountNumber, UUID targetAccountId,
                              String targetAccountNumber, BigDecimal amount, String currency, Instant occurredOn,
                              String status) {

    public TransferSummary {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(sourceAccountNumber, "sourceAccountNumber");
        Objects.requireNonNull(targetAccountId, "targetAccountId");
        Objects.requireNonNull(targetAccountNumber, "targetAccountNumber");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredOn, "occurredOn");
        Objects.requireNonNull(status, "status");
    }

    /** Si la retirada en origen se aprobó. Los adaptadores lo usan en vez de comparar el texto del estado (#82). */
    public boolean approved() {
        return "APPROVED".equals(status);
    }

    /** Si la retirada en origen se rechazó. */
    public boolean rejected() {
        return "REJECTED".equals(status);
    }
}

package com.dawnrock.ddd.adapter.out.event;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del outbox de eventos (#165, ADR 0021). El intervalo del relé ({@code app.events.outbox.relay-interval},
 * 2 s por defecto) lo lee directamente su {@code @Scheduled}.
 *
 * @param batchSize   eventos pendientes que el relé entrega en cada pasada (100 por defecto)
 * @param maxAttempts intentos fallidos tras los que un evento se da por fallido y no se reintenta (5 por defecto)
 * @param retention   tiempo que se guardan los eventos ya publicados antes de borrarlos (7 días por defecto)
 */
@ConfigurationProperties(prefix = "app.events.outbox")
record OutboxProperties(Integer batchSize, Integer maxAttempts, Duration retention) {

    static final int DEFAULT_BATCH_SIZE = 100;
    static final int DEFAULT_MAX_ATTEMPTS = 5;
    static final Duration DEFAULT_RETENTION = Duration.ofDays(7);

    OutboxProperties {
        batchSize = Objects.requireNonNullElse(batchSize, DEFAULT_BATCH_SIZE);
        maxAttempts = Objects.requireNonNullElse(maxAttempts, DEFAULT_MAX_ATTEMPTS);
        retention = Objects.requireNonNullElse(retention, DEFAULT_RETENTION);
        if (batchSize < 1 || maxAttempts < 1 || retention.isNegative() || retention.isZero()) {
            throw new IllegalStateException("La configuración del outbox de eventos debe ser positiva");
        }
    }
}

package com.dawnrock.ddd.adapter.out.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila del outbox de eventos de dominio (#165, ADR 0021): un evento guardado con su agregado, pendiente hasta que el relé
 * lo entrega ({@code publishedAt}) o se rinde tras demasiados intentos ({@code failedAt}). El {@code id} da el orden de
 * entrega.
 */
@Entity
@Table(name = "domain_event_outbox")
class OutboxEventEntity {

    /** Longitud de la columna {@code last_error}: el mensaje del último fallo se recorta para que quepa. */
    static final int LAST_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String eventType;
    UUID accountId;
    String payload;
    Instant occurredOn;
    Instant recordedAt;
    Instant publishedAt;
    int attempts;
    Instant failedAt;
    String lastError;

    protected OutboxEventEntity() {
    }

    OutboxEventEntity(String eventType, UUID accountId, String payload, Instant occurredOn, Instant recordedAt) {
        this.eventType = eventType;
        this.accountId = accountId;
        this.payload = payload;
        this.occurredOn = occurredOn;
        this.recordedAt = recordedAt;
    }

    /** El relé lo ha entregado: deja de estar pendiente. */
    void markPublished(Instant at) {
        publishedAt = at;
    }

    /**
     * Un consumidor ha fallado al recibirlo: cuenta el intento y guarda el error. Al llegar a {@code maxAttempts} el
     * evento se da por fallido y no se vuelve a intentar.
     */
    void recordFailedAttempt(String error, int maxAttempts, Instant at) {
        attempts++;
        lastError = error == null || error.length() <= LAST_ERROR_LENGTH ? error : error.substring(0, LAST_ERROR_LENGTH);
        if (attempts >= maxAttempts) {
            failedAt = at;
        }
    }
}

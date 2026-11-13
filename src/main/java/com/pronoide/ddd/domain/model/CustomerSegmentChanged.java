package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento de dominio: el titular de una cuenta ha cambiado de segmento, por alcanzar el patrimonio mínimo (pasa a alto
 * patrimonio) o por una revisión que no le permite conservarlo (vuelve a minorista).
 */
public record CustomerSegmentChanged(UUID accountId, String holderName, CustomerSegment segment, Instant occurredOn)
        implements DomainEvent {

    public CustomerSegmentChanged {
        Objects.requireNonNull(accountId, "La cuenta del evento es obligatoria");
        Objects.requireNonNull(holderName, "El titular del evento es obligatorio");
        Objects.requireNonNull(segment, "El segmento del evento es obligatorio");
        Objects.requireNonNull(occurredOn, "El instante del evento es obligatorio");
    }
}

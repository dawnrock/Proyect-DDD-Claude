package com.pronoide.ddd.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Algo relevante que ya ha ocurrido en una cuenta. Lo registra el agregado {@link CheckingAccount} al cambiar de estado
 * y lo publica la aplicación, después de guardarlo, por el puerto {@link DomainEventPublisher}. Inmutable.
 */
public interface DomainEvent {

    /** Cuenta en la que ha ocurrido. */
    UUID accountId();

    /** Instante en que ocurrió. */
    Instant occurredOn();
}

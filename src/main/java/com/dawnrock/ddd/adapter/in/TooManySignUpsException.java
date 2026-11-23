package com.dawnrock.ddd.adapter.in;

import java.time.Duration;

/**
 * Alta de cliente rechazada por el límite de altas por IP o en total (#131, ADR 0018). {@link #retryAfter()} es lo que
 * falta para que haya sitio. El mensaje no dice qué límite se alcanzó.
 */
public class TooManySignUpsException extends RuntimeException {

    private final transient Duration retryAfter;

    public TooManySignUpsException(Duration retryAfter) {
        super("Demasiadas altas de clientes en este momento: inténtalo de nuevo más tarde");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}

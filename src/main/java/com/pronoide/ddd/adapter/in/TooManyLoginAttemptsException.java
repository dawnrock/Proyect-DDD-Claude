package com.pronoide.ddd.adapter.in;

import java.time.Duration;

import org.springframework.security.core.AuthenticationException;

/**
 * Inicio de sesión rechazado por demasiados intentos fallidos (#130, ADR 0017). El mensaje es el mismo exista o no el
 * usuario, para no revelarlo; {@link #retryAfter()} es lo que le queda al bloqueo.
 */
public class TooManyLoginAttemptsException extends AuthenticationException {

    private final transient Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Demasiados intentos fallidos de inicio de sesión");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}

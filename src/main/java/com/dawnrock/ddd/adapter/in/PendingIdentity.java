package com.dawnrock.ddd.adapter.in;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Una identidad que Auth0 ya ha verificado pero que aún no está vinculada a ningún usuario local (ADR 0024). Se guarda
 * en la sesión HTTP ({@link #SESSION_ATTRIBUTE}) mientras el cliente la vincula en {@code /login/link}, y caduca a los
 * {@link #VALIDITY} minutos. Contrato: {@code docs/contracts/auth0-web-login.md}.
 *
 * @param provider  proveedor ({@link ExternalIdentities#AUTH0})
 * @param subject   el {@code sub} del token de identidad
 * @param email     el correo que da Auth0, solo para enseñárselo al cliente (puede ser {@code null}); nunca se usa para
 *                  vincular
 * @param createdAt cuándo volvió de Auth0
 */
public record PendingIdentity(String provider, String subject, String email, Instant createdAt) implements Serializable {

    /** Atributo de la sesión HTTP donde espera la identidad pendiente. */
    public static final String SESSION_ATTRIBUTE = PendingIdentity.class.getName();

    /** Cuánto tiempo puede esperar en la sesión antes de tener que volver a entrar con Auth0. */
    public static final Duration VALIDITY = Duration.ofMinutes(10);

    /** Si ya pasaron {@link #VALIDITY} desde {@code createdAt} según el reloj. */
    public boolean isExpired(Clock clock) {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }
}

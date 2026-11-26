package com.dawnrock.ddd.adapter.in;

/**
 * La identidad de Auth0 pendiente de vincular ha caducado ({@link PendingIdentity#VALIDITY}) mientras el cliente
 * escribía su contraseña (ADR 0024). La web lleva a {@code /login?link-expired} para volver a entrar con Auth0.
 * Contrato: {@code docs/contracts/auth0-web-login.md}.
 */
public class PendingIdentityExpiredException extends RuntimeException {

    public PendingIdentityExpiredException(String provider) {
        super("La identidad pendiente de %s ha caducado: vuelve a entrar".formatted(provider));
    }
}

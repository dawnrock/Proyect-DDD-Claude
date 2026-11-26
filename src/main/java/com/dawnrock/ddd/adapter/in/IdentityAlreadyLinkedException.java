package com.dawnrock.ddd.adapter.in;

/**
 * El usuario local ya tiene vinculada otra identidad del mismo proveedor, o la identidad ya es de otro usuario (ADR
 * 0024). La web lo explica en la página de vinculación. Contrato: {@code docs/contracts/auth0-web-login.md}.
 */
public class IdentityAlreadyLinkedException extends RuntimeException {

    public IdentityAlreadyLinkedException(String provider, String username) {
        super("El usuario %s ya está vinculado a otra cuenta de %s".formatted(username, provider));
    }
}

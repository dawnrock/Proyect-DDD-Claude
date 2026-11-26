package com.dawnrock.ddd.adapter.in;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * Auth0 ha verificado la identidad, pero no está vinculada a ningún usuario local (ADR 0024). La lanza
 * {@link LinkedOidcUserService}; el manejador de fallos del inicio de sesión de la web guarda la {@link #pending()} en
 * la sesión y lleva a {@code /login/link}. Contrato: {@code docs/contracts/auth0-web-login.md}.
 */
public class UnlinkedIdentityException extends OAuth2AuthenticationException {

    /** Código del error OAuth2. */
    public static final String ERROR_CODE = "unlinked_identity";

    private final PendingIdentity pending;

    public UnlinkedIdentityException(PendingIdentity pending) {
        super(new OAuth2Error(ERROR_CODE), "La identidad de %s no está vinculada a ningún usuario"
                .formatted(pending.provider()));
        this.pending = pending;
    }

    public PendingIdentity pending() {
        return pending;
    }
}

package com.dawnrock.ddd.adapter.in;

import org.springframework.stereotype.Component;

/**
 * Vincula una identidad de Auth0 pendiente con un usuario local, comprobando una sola vez su usuario y su contraseña
 * con el mismo proveedor limitado en intentos que el inicio de sesión (ADR 0017 y 0024). Contrato:
 * {@code docs/contracts/auth0-web-login.md}.
 */
@Component
public class ExternalIdentityLinking {

    /**
     * Comprueba la contraseña del usuario local y vincula la identidad pendiente.
     *
     * @param clientIp IP del cliente, para el límite de intentos (la da {@link ClientIpResolver})
     * @return el usuario local, normalizado ({@link Usernames#normalize})
     * @throws org.springframework.security.authentication.BadCredentialsException si el usuario o la contraseña no son
     *                                                                             correctos (cuenta como intento fallido)
     * @throws TooManyLoginAttemptsException si el usuario o la IP están bloqueados
     * @throws IdentityAlreadyLinkedException si el usuario ya tiene otra identidad de Auth0
     * @throws IllegalStateException si la identidad pendiente ha caducado
     */
    public String link(PendingIdentity pending, String username, String rawPassword, String clientIp) {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }
}

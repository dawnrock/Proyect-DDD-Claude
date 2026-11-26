package com.dawnrock.ddd.adapter.in;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Vincula una identidad de Auth0 pendiente con un usuario local, comprobando una sola vez su usuario y su contraseña
 * con el mismo proveedor limitado en intentos que el inicio de sesión (ADR 0017 y 0024). Contrato:
 * {@code docs/contracts/auth0-web-login.md}.
 */
@Component
public class ExternalIdentityLinking {

    private final ThrottledAuthenticationProvider authentication;
    private final ExternalIdentities identities;
    private final ObjectProvider<Clock> clock;

    /** @param clock el reloj del núcleo; sin él, como en los tests web, el del sistema */
    public ExternalIdentityLinking(ThrottledAuthenticationProvider authentication, ExternalIdentities identities,
                                   ObjectProvider<Clock> clock) {
        this.authentication = authentication;
        this.identities = identities;
        this.clock = clock;
    }

    /**
     * Comprueba la contraseña del usuario local y vincula la identidad pendiente.
     *
     * @param clientIp IP del cliente, para el límite de intentos (la da {@link ClientIpResolver})
     * @return el usuario local, normalizado ({@link Usernames#normalize})
     * @throws org.springframework.security.authentication.BadCredentialsException si el usuario o la contraseña no son
     *                                                                             correctos (cuenta como intento fallido)
     * @throws TooManyLoginAttemptsException si el usuario o la IP están bloqueados
     * @throws IdentityAlreadyLinkedException si el usuario ya tiene otra identidad de Auth0
     * @throws org.springframework.security.core.AuthenticationException si la cuenta local está deshabilitada, bloqueada
     *                                                                    o caducada
     * @throws PendingIdentityExpiredException si la identidad pendiente ha caducado (no cuenta como intento)
     */
    public String link(PendingIdentity pending, String username, String rawPassword, String clientIp) {
        if (pending.isExpired(clock.getIfAvailable(Clock::systemUTC))) {
            throw new PendingIdentityExpiredException(pending.provider());
        }
        UsernamePasswordAuthenticationToken attempt =
                UsernamePasswordAuthenticationToken.unauthenticated(Usernames.normalize(username), rawPassword);
        attempt.setDetails(new WebAuthenticationDetails(clientIp, null));
        Authentication result = authentication.authenticate(attempt);
        if (result == null) {
            throw new IllegalStateException("El proveedor de autenticación no admite usuario y contraseña");
        }
        identities.link(pending.provider(), pending.subject(), result.getName());
        return result.getName();
    }
}

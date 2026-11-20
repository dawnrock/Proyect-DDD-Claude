package com.pronoide.ddd.adapter.in;

import java.time.Duration;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Proveedor de autenticación con límite de intentos (#130, ADR 0017), que envuelve al de usuario y contraseña. Lo usan
 * el inicio de sesión de la web y la emisión de tokens de la API: los dos llegan aquí con la IP en los detalles de la
 * autenticación ({@link WebAuthenticationDetails}).
 *
 * <p>Mientras la pareja usuario e IP, o la IP, están bloqueadas, rechaza con {@link TooManyLoginAttemptsException} sin
 * comprobar la contraseña, aunque sea la correcta. Un usuario que no existe cuenta igual que uno que existe.
 */
@Slf4j
public class ThrottledAuthenticationProvider implements AuthenticationProvider {

    static final String UNKNOWN_IP = "unknown";

    private final AuthenticationProvider delegate;
    private final LoginAttempts attempts;

    public ThrottledAuthenticationProvider(AuthenticationProvider delegate, LoginAttempts attempts) {
        this.delegate = delegate;
        this.attempts = attempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        String ip = ipOf(authentication);
        // Se reserva el intento antes de comprobar la contraseña: una ráfaga simultánea no pasa del límite
        Optional<Duration> locked = attempts.tryAttempt(username, ip);
        if (locked.isPresent()) {
            throw new TooManyLoginAttemptsException(locked.get());
        }
        try {
            Authentication result = delegate.authenticate(authentication);
            attempts.succeeded(username, ip);
            return result;
        } catch (BadCredentialsException e) {
            // Un usuario inexistente también llega como BadCredentialsException (hideUserNotFoundExceptions)
            if (attempts.lockedFor(username, ip).isPresent()) {
                log.warn("Bloqueados los inicios de sesión de '{}' desde {} por demasiados intentos fallidos",
                        forLog(username), ip);
            }
            throw e;
        } catch (RuntimeException e) {
            // Cuenta deshabilitada, caducada...: no es una contraseña probada, así que no cuenta como fallo
            attempts.released(username, ip);
            throw e;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    /** El usuario lo escribe quien intenta entrar: sin caracteres de control y recortado, para no falsear el log. */
    private static String forLog(String username) {
        String clean = String.valueOf(username).replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "_");
        return clean.length() > LoginAttempts.MAX_USERNAME_IN_KEY
                ? clean.substring(0, LoginAttempts.MAX_USERNAME_IN_KEY) + "…" : clean;
    }

    private static String ipOf(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details
                && details.getRemoteAddress() != null) {
            return details.getRemoteAddress();
        }
        return UNKNOWN_IP;
    }
}

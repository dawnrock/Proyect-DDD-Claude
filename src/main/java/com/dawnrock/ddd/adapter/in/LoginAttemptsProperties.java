package com.dawnrock.ddd.adapter.in;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de intentos de inicio de sesión ({@code app.security.login-attempts.*}, #130, ADR 0017).
 *
 * @param maxPerUser fallos seguidos de un usuario desde una IP que la bloquean para ese usuario (3 por defecto)
 * @param maxPerIp   fallos desde una IP, con cualquier usuario, que la bloquean entera (10 por defecto)
 * @param lockout    duración del bloqueo y de la ventana en la que cuentan los fallos (30 minutos por defecto)
 */
@ConfigurationProperties(prefix = "app.security.login-attempts")
public record LoginAttemptsProperties(Integer maxPerUser, Integer maxPerIp, Duration lockout) {

    static final int DEFAULT_MAX_PER_USER = 3;
    static final int DEFAULT_MAX_PER_IP = 10;
    static final Duration DEFAULT_LOCKOUT = Duration.ofMinutes(30);

    public LoginAttemptsProperties {
        maxPerUser = maxPerUser == null ? DEFAULT_MAX_PER_USER : maxPerUser;
        maxPerIp = maxPerIp == null ? DEFAULT_MAX_PER_IP : maxPerIp;
        lockout = lockout == null ? DEFAULT_LOCKOUT : lockout;
        if (maxPerUser < 1 || maxPerIp < 1 || lockout.isNegative() || lockout.isZero()) {
            throw new IllegalStateException("El límite de intentos de inicio de sesión debe ser positivo");
        }
    }
}

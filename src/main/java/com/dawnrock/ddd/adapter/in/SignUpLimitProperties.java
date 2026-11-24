package com.dawnrock.ddd.adapter.in;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de altas de clientes ({@code app.security.sign-ups.*}, #131 y #139, ADR 0018).
 *
 * @param maxPerIp altas desde una IP dentro de la ventana (3 por defecto)
 * @param maxTotal altas desde todas las IP juntas dentro de la ventana (50 por defecto): acota la memoria que ocupan las
 *                 altas aunque se usen muchas IP
 * @param window   ventana deslizante en la que cuentan las altas y los fallos (una hora por defecto)
 * @param maxFailuresPerIp altas fallidas desde una IP que revelan un usuario o un documento existentes, dentro de la
 *                 ventana, tras las que esa IP no puede intentar más altas (10 por defecto, #139)
 */
@ConfigurationProperties(prefix = "app.security.sign-ups")
public record SignUpLimitProperties(Integer maxPerIp, Integer maxTotal, Duration window, Integer maxFailuresPerIp) {

    static final int DEFAULT_MAX_PER_IP = 3;
    static final int DEFAULT_MAX_TOTAL = 50;
    static final Duration DEFAULT_WINDOW = Duration.ofHours(1);
    static final int DEFAULT_MAX_FAILURES_PER_IP = 10;

    public SignUpLimitProperties {
        maxPerIp = Objects.requireNonNullElse(maxPerIp, DEFAULT_MAX_PER_IP);
        maxTotal = Objects.requireNonNullElse(maxTotal, DEFAULT_MAX_TOTAL);
        window = Objects.requireNonNullElse(window, DEFAULT_WINDOW);
        maxFailuresPerIp = Objects.requireNonNullElse(maxFailuresPerIp, DEFAULT_MAX_FAILURES_PER_IP);
        if (maxPerIp < 1 || maxTotal < 1 || maxFailuresPerIp < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("El límite de altas de clientes debe ser positivo");
        }
        if (maxTotal < maxPerIp) {
            throw new IllegalStateException("El límite total de altas no puede ser menor que el de una IP");
        }
    }
}

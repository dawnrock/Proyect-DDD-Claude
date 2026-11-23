package com.pronoide.ddd.adapter.in;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de altas de clientes ({@code app.security.sign-ups.*}, #131, ADR 0018).
 *
 * @param maxPerIp altas desde una IP dentro de la ventana (3 por defecto)
 * @param maxTotal altas desde todas las IP juntas dentro de la ventana (50 por defecto): acota la memoria que ocupan las
 *                 altas aunque se usen muchas IP
 * @param window   ventana deslizante en la que cuentan las altas (una hora por defecto)
 */
@ConfigurationProperties(prefix = "app.security.sign-ups")
public record SignUpLimitProperties(Integer maxPerIp, Integer maxTotal, Duration window) {

    static final int DEFAULT_MAX_PER_IP = 3;
    static final int DEFAULT_MAX_TOTAL = 50;
    static final Duration DEFAULT_WINDOW = Duration.ofHours(1);

    public SignUpLimitProperties {
        maxPerIp = maxPerIp == null ? DEFAULT_MAX_PER_IP : maxPerIp;
        maxTotal = maxTotal == null ? DEFAULT_MAX_TOTAL : maxTotal;
        window = window == null ? DEFAULT_WINDOW : window;
        if (maxPerIp < 1 || maxTotal < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("El límite de altas de clientes debe ser positivo");
        }
        if (maxTotal < maxPerIp) {
            throw new IllegalStateException("El límite total de altas no puede ser menor que el de una IP");
        }
    }
}

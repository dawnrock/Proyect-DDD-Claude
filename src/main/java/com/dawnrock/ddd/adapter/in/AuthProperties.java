package com.dawnrock.ddd.adapter.in;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Autenticación local ({@code app.auth.*}, ADR 0024).
 *
 * @param local si se aceptan los usuarios con contraseña de la aplicación en el formulario de inicio de sesión. Por
 *              defecto, sí; la issue #199 lo desactivará en {@code prod}
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(@DefaultValue("true") boolean local) {
}

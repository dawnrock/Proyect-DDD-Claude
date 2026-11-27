package com.dawnrock.ddd.adapter.in;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Autenticación local ({@code app.auth.*}, ADR 0024).
 *
 * @param local si se aceptan los usuarios con contraseña de la aplicación en el formulario de inicio de sesión. Por
 *              defecto, sí; el perfil {@code prod} lo desactiva: es el modo realista, solo con Auth0 (#199)
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(@DefaultValue("true") boolean local) {
}

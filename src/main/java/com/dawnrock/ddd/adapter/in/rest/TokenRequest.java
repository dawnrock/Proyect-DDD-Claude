package com.dawnrock.ddd.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code POST /api/auth/token} (#122): las credenciales de un usuario. Es un {@code record} por la misma
 * razón que {@link OpenAccountRequest}. La contraseña solo viaja aquí, nunca en las demás peticiones de la API.
 */
public record TokenRequest(
        @NotBlank(message = "El usuario es obligatorio")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        String password) {

    /** Sin la contraseña, para que no acabe en un log. */
    @Override
    public String toString() {
        return "TokenRequest[username=%s]".formatted(username);
    }
}

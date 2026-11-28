package com.dawnrock.ddd.adapter.in.rest;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Respuesta de {@code POST /api/auth/token} (esquema {@code AccessToken} del OpenAPI, #122). Los campos van en orden
 * alfabético, como los escribía Immutables (#219): en un record mandaría el orden de sus componentes.
 *
 * @param accessToken el JWT firmado, para enviarlo en {@code Authorization: Bearer}
 * @param tokenType   siempre {@code Bearer}
 * @param expiresIn   segundos hasta que caduca
 */
@JsonPropertyOrder({"accessToken", "expiresIn", "tokenType"})
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public TokenResponse {
        Objects.requireNonNull(accessToken, "accessToken");
        Objects.requireNonNull(tokenType, "tokenType");
    }
}

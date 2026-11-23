package com.dawnrock.ddd.adapter.in.rest;

import org.immutables.value.Value;

import com.dawnrock.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonSerialize;

/** Respuesta de {@code POST /api/auth/token} (esquema {@code AccessToken} del OpenAPI, #122). */
@ApplicationValue
@Value.Immutable
@JsonSerialize(as = TokenResponse.class)
public interface TokenResponseDef {

    /** El JWT firmado, para enviarlo en {@code Authorization: Bearer}. */
    String accessToken();

    /** Siempre {@code Bearer}. */
    String tokenType();

    /** Segundos hasta que caduca. */
    long expiresIn();
}

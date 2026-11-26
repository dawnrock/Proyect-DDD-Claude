package com.dawnrock.ddd.adapter.in.rest;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;

/**
 * Un token de acceso de Auth0 válido cuyo {@code sub} no tiene usuario local: sin vincular, o de un usuario que ya no
 * existe o está deshabilitado (#197, ADR 0024). Es un {@code invalid_token} de RFC 6750 (401); la descripción del error
 * va en ASCII, como exige la cabecera {@code WWW-Authenticate}, y el {@code ProblemDetail} dice en español cómo
 * vincular la identidad ({@link Auth0AccessTokens#UNLINKED_DETAIL}).
 */
public class UnlinkedAccessTokenException extends OAuth2AuthenticationException {

    public UnlinkedAccessTokenException() {
        super(BearerTokenErrors.invalidToken("The Auth0 identity is not linked to any user"));
    }
}

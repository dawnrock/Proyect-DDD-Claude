package com.dawnrock.ddd.adapter.in.rest;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * No se pueden comprobar los tokens de Auth0 ahora mismo: las claves públicas del tenant ({@code jwks.json}) no se
 * pueden descargar (#197). No dice nada del token, así que la API responde 503 y no pide otro token. Es una
 * {@link OAuth2AuthenticationException} para que llegue al punto de entrada de la cadena: Spring vuelve a lanzar las
 * {@code AuthenticationServiceException}, que acabarían en un 500.
 */
public class Auth0UnavailableException extends OAuth2AuthenticationException {

    public Auth0UnavailableException(Throwable cause) {
        super(new OAuth2Error("temporarily_unavailable"), "No se pueden leer las claves de Auth0", cause);
    }
}

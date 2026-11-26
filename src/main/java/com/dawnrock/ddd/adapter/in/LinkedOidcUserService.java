package com.dawnrock.ddd.adapter.in;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Traduce la identidad de Auth0 al usuario local (ADR 0024): si el {@code sub} está vinculado, devuelve un
 * {@link OidcUser} que conserva el token de identidad y sus claims, pero cuyo {@code getName()} es el usuario local y
 * cuyas autoridades son sus {@code ROLE_*} de la base de datos. Si no está vinculado, lanza
 * {@link UnlinkedIdentityException}. Es un bean solo cuando Auth0 está configurado. Contrato:
 * {@code docs/contracts/auth0-web-login.md}.
 */
public class LinkedOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        throw new UnsupportedOperationException("pendiente: backend-dev");
    }
}

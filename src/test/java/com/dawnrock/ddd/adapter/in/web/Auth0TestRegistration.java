package com.dawnrock.ddd.adapter.in.web;

import java.util.Map;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * El registro {@code auth0} de un dominio falso, sin red, con los endpoints de {@code docs/contracts/auth0-web-login.md}
 * (ADR 0024), para los tests web que necesitan {@code oauth2Login()}.
 */
@TestConfiguration
class Auth0TestRegistration {

    static final String DOMAIN = "tenant.example.auth0.com";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration auth0 = ClientRegistration.withRegistrationId("auth0")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://" + DOMAIN + "/authorize")
                .tokenUri("https://" + DOMAIN + "/oauth/token")
                .userInfoUri("https://" + DOMAIN + "/userinfo")
                .jwkSetUri("https://" + DOMAIN + "/.well-known/jwks.json")
                .issuerUri("https://" + DOMAIN + "/")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", "https://" + DOMAIN + "/oidc/logout"))
                .userNameAttributeName("sub")
                .build();
        return new InMemoryClientRegistrationRepository(auth0);
    }
}

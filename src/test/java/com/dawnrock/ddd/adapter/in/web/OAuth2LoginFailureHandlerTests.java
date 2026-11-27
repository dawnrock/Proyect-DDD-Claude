package com.dawnrock.ddd.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.UnlinkedIdentityException;

/**
 * El registro de los fallos del retorno de Auth0 (#199, T1): un aviso con el código del error para poder diagnosticarlo,
 * sin tokens ni datos personales; una identidad sin vincular no es un fallo y no deja aviso.
 */
@ExtendWith(OutputCaptureExtension.class)
class OAuth2LoginFailureHandlerTests {

    private final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler();

    @Test
    @DisplayName("Un fallo de OAuth2 deja un aviso con su código, sin la descripción, y sigue llevando a /login?oauth-error")
    void anOAuth2FailureLogsItsErrorCodeWithoutTheDescription(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2Error error = new OAuth2Error("authorization_request_not_found", "ada@example.com auth0|123", null);

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new OAuth2AuthenticationException(error));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauth-error");
        assertThat(output.getAll())
                .contains("WARN")
                .contains("authorization_request_not_found")
                .doesNotContain("ada@example.com")
                .doesNotContain("auth0|123");
    }

    @Test
    @DisplayName("Un fallo sin código de OAuth2 deja un aviso con el tipo de la excepción, sin su mensaje")
    void aFailureWithoutAnOAuth2CodeLogsTheExceptionTypeWithoutItsMessage(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("usuario ada desactivado"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?oauth-error");
        assertThat(output.getAll())
                .contains("WARN")
                .contains("BadCredentialsException")
                .doesNotContain("usuario ada desactivado");
    }

    @Test
    @DisplayName("Una identidad sin vincular lleva a /login/link sin dejar ningún aviso")
    void anUnlinkedIdentityLogsNothing(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        PendingIdentity pending = new PendingIdentity("auth0", "auth0|123", "ada@example.com", Instant.now());

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, new OAuth2AuthenticationException(
                new OAuth2Error("invalid_user_info_response"), new UnlinkedIdentityException(pending)));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login/link");
        assertThat(output.getAll()).doesNotContain(OAuth2LoginFailureHandler.class.getSimpleName());
    }
}

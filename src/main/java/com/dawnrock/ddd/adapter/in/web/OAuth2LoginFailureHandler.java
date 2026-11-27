package com.dawnrock.ddd.adapter.in.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.UnlinkedIdentityException;

import lombok.extern.slf4j.Slf4j;

/**
 * Qué hace la web cuando falla el retorno de Auth0 (#196, ADR 0024). Si la causa es una identidad sin vincular
 * ({@link UnlinkedIdentityException}, que Spring envuelve en otra excepción: se busca en la cadena de causas), guarda
 * su {@link PendingIdentity} en la sesión y lleva a {@code /login/link}. Cualquier otro fallo (estado, token, usuario
 * local ausente o desactivado…) lleva a {@code /login?oauth-error} y deja un aviso en el log con el código del error
 * OAuth2 (o el tipo de la excepción), para poder diagnosticarlo (#199). Nunca la descripción ni el mensaje: pueden
 * llevar el correo o el {@code sub} del cliente.
 */
@Slf4j
final class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof UnlinkedIdentityException unlinked) {
                request.getSession().setAttribute(PendingIdentity.SESSION_ATTRIBUTE, unlinked.pending());
                response.sendRedirect(request.getContextPath() + "/login/link");
                return;
            }
            cause = cause == cause.getCause() ? null : cause.getCause();
        }
        log.warn("Falló el retorno de Auth0: {}", reason(exception));
        response.sendRedirect(request.getContextPath() + "/login?oauth-error");
    }

    /** El código del error OAuth2 (por ejemplo {@code authorization_request_not_found}) o, si no lo hay, el tipo. */
    private static String reason(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth2
                ? oauth2.getError().getErrorCode()
                : exception.getClass().getSimpleName();
    }
}

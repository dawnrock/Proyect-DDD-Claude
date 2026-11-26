package com.dawnrock.ddd.adapter.in.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import com.dawnrock.ddd.adapter.in.PendingIdentity;
import com.dawnrock.ddd.adapter.in.UnlinkedIdentityException;

/**
 * Qué hace la web cuando falla el retorno de Auth0 (#196, ADR 0024). Si la causa es una identidad sin vincular
 * ({@link UnlinkedIdentityException}, que Spring envuelve en otra excepción: se busca en la cadena de causas), guarda
 * su {@link PendingIdentity} en la sesión y lleva a {@code /login/link}. Cualquier otro fallo (estado, token, usuario
 * local ausente o desactivado…) lleva a {@code /login?oauth-error}.
 */
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
        response.sendRedirect(request.getContextPath() + "/login?oauth-error");
    }
}

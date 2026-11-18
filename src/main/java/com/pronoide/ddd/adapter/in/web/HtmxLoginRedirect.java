package com.pronoide.ddd.adapter.in.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

/**
 * Sesión caducada en una petición de htmx (#125). Responder con la redirección al login haría que htmx pintase la página
 * de login dentro del panel de la cuenta; en su lugar se responde 401 con {@code HX-Redirect}, y htmx navega al login.
 *
 * <p>Cubre los dos caminos de una sesión caducada: sin autenticación ({@link AuthenticationEntryPoint}) y, en un POST,
 * sin el token CSRF que vivía en la sesión ({@link MissingCsrfTokenException}). Un token CSRF inválido u otra denegación
 * siguen yendo al manejador de siempre (la página 403), igual que cualquier petición que no sea de htmx.
 */
final class HtmxLoginRedirect implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String HX_REQUEST = "HX-Request";

    private final String loginUrl;

    private final AuthenticationEntryPoint loginPage;

    private final AccessDeniedHandler errorPage;

    /**
     * @param loginUrl  página de login
     * @param loginPage lo que reciben las peticiones que no son de htmx sin sesión (la redirección al login)
     * @param errorPage lo que reciben el resto de denegaciones (la página 403)
     */
    HtmxLoginRedirect(String loginUrl, AuthenticationEntryPoint loginPage, AccessDeniedHandler errorPage) {
        this.loginUrl = loginUrl;
        this.loginPage = loginPage;
        this.errorPage = errorPage;
    }

    static boolean isHtmx(HttpServletRequest request) {
        return "true".equals(request.getHeader(HX_REQUEST));
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        if (isHtmx(request)) {
            redirectToLogin(request, response);
        } else {
            loginPage.commence(request, response, authException);
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        if (isHtmx(request) && accessDeniedException instanceof MissingCsrfTokenException) {
            redirectToLogin(request, response);
        } else {
            errorPage.handle(request, response, accessDeniedException);
        }
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("HX-Redirect", request.getContextPath() + loginUrl);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}

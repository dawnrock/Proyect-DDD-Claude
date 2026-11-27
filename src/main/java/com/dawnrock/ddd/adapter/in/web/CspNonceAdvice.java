package com.dawnrock.ddd.adapter.in.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pone en el modelo de todas las páginas el nonce de la petición ({@code cspNonce}), para el {@code th:nonce} de sus
 * scripts en línea (#206): hoy, el import map de la intro del login.
 */
@ControllerAdvice
public class CspNonceAdvice {

    @ModelAttribute("cspNonce")
    public String cspNonce(HttpServletRequest request) {
        return CspNonce.of(request);
    }
}

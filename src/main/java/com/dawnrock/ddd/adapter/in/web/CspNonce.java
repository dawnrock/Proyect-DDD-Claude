package com.dawnrock.ddd.adapter.in.web;

import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;

/**
 * El nonce de la Content-Security-Policy de una petición (#206, ADR 0025): 16 bytes aleatorios en base64url, creados la
 * primera vez que alguien lo pide y guardados en la propia petición. Así la plantilla que lo pinta en su
 * {@code <script>} ({@link CspNonceAdvice}) y la cabecera que lo autoriza ({@link WebSecurityConfig}) usan el mismo,
 * pidan antes una u otra.
 */
public final class CspNonce {

    static final String ATTRIBUTE = CspNonce.class.getName();

    private static final int BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CspNonce() {
    }

    /** El nonce de esta petición; si aún no tiene, lo crea. */
    public static String of(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof String nonce) {
            return nonce;
        }
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        request.setAttribute(ATTRIBUTE, nonce);
        return nonce;
    }
}

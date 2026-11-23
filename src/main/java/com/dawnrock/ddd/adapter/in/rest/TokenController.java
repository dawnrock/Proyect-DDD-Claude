package com.dawnrock.ddd.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dawnrock.ddd.adapter.in.ClientIpAuthenticationDetailsSource;

/**
 * Emisión de tokens de la API: {@code POST /api/auth/token}, público (#122, ADR 0015). Las credenciales erróneas las
 * traduce {@link RestExceptionHandler} a 401, y el bloqueo por demasiados intentos (#130), a 429. El token se envía después en {@code Authorization: Bearer}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final AccessTokenIssuer issuer;

    /** La IP del cliente detrás de Render y Cloudflare, para el límite de intentos (#133). */
    private final ClientIpAuthenticationDetailsSource details;

    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request, HttpServletRequest http) {
        // La IP de la petición cuenta para el límite de intentos fallidos (#130, ADR 0017)
        return issuer.issue(request.username(), request.password(), details.buildDetails(http));
    }
}

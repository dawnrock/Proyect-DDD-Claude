package com.pronoide.ddd.adapter.in.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Emisión de tokens de la API: {@code POST /api/auth/token}, público (#122, ADR 0015). Las credenciales erróneas las
 * traduce {@link RestExceptionHandler} a 401. El token se envía después en {@code Authorization: Bearer}.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final AccessTokenIssuer issuer;

    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        return issuer.issue(request.username(), request.password());
    }
}

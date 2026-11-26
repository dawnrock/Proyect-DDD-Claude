package com.dawnrock.ddd.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;

/** Cada emisor de confianza tiene un único gestor (#197 revisión): dos con el mismo {@code iss} es un error. */
class ApiIssuersTests {

    @Test
    @DisplayName("Registrar dos gestores para el mismo emisor impide arrancar en vez de pisar uno con otro")
    void twoManagersForTheSameIssuerFailInsteadOfOverwriting() {
        Map<String, AuthenticationManager> managers = new HashMap<>();
        ApiSecurityConfig.trust(managers, "https://tenant.example.auth0.com/", mock(AuthenticationManager.class));

        assertThatIllegalStateException()
                .isThrownBy(() -> ApiSecurityConfig.trust(managers, "https://tenant.example.auth0.com/",
                        mock(AuthenticationManager.class)))
                .withMessageContaining("https://tenant.example.auth0.com/");
    }
}

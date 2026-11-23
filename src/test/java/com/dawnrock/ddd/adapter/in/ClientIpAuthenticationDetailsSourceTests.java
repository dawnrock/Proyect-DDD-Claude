package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Detalles de la autenticación con la IP del cliente (#133, ADR 0017). */
class ClientIpAuthenticationDetailsSourceTests {

    private final ClientIpAuthenticationDetailsSource source = new ClientIpAuthenticationDetailsSource(
            new ClientIpResolver(List.of("10.0.0.0/8", "104.16.0.0/13")));

    @Test
    @DisplayName("Con varias cabeceras X-Forwarded-For se unen en orden: la del cliente no puede elegir su IP")
    void con_varias_cabeceras_x_forwarded_for_se_unen_en_orden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        // La primera la escribe el cliente; la segunda, Cloudflare, con la IP real del cliente
        request.addHeader("X-Forwarded-For", "203.0.113.99");
        request.addHeader("X-Forwarded-For", "46.6.116.139, 104.22.136.75");

        assertThat(source.buildDetails(request).getRemoteAddress()).isEqualTo("46.6.116.139");
    }

    @Test
    @DisplayName("Sin cabecera, la IP es la de la conexión, y la sesión se conserva")
    void sin_cabecera_la_ip_es_la_de_la_conexion_y_la_sesion_se_conserva() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("46.6.116.139");
        String sessionId = request.getSession(true).getId();

        assertThat(source.buildDetails(request).getRemoteAddress()).isEqualTo("46.6.116.139");
        assertThat(source.buildDetails(request).getSessionId()).isEqualTo(sessionId);
    }
}

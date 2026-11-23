package com.pronoide.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** IP del cliente detrás de los proxies de confianza (#133, ADR 0017): el proxy interno de Render y Cloudflare. */
class ClientIpResolverTests {

    private static final String RENDER_PROXY = "10.201.3.4";
    private static final String CLOUDFLARE_EDGE = "104.22.136.75";
    private static final String CLIENT = "46.6.116.139";

    private final ClientIpResolver resolver = new ClientIpResolver(List.of(
            "10.0.0.0/8", "127.0.0.0/8", "104.16.0.0/13", "2606:4700::/32"));

    @Test
    @DisplayName("Un cliente que no llega a través de un proxy de confianza es la IP; su X-Forwarded-For se ignora")
    void un_cliente_directo_ignora_x_forwarded_for() {
        assertThat(resolver.resolve(CLIENT, "1.2.3.4")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("A través del proxy de Render y de Cloudflare, la IP es la del cliente")
    void a_traves_de_render_y_cloudflare_la_ip_es_la_del_cliente() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ", " + CLOUDFLARE_EDGE)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Tras la válvula de Tomcat, que ya dejó la IP de Cloudflare como conexión, la IP sigue siendo la del cliente")
    void tras_la_valvula_de_tomcat_la_ip_sigue_siendo_la_del_cliente() {
        assertThat(resolver.resolve(CLOUDFLARE_EDGE, CLIENT)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Las direcciones que añade el propio cliente a X-Forwarded-For quedan a la izquierda y no cuentan")
    void las_direcciones_que_anade_el_cliente_no_cuentan() {
        assertThat(resolver.resolve(RENDER_PROXY, "1.2.3.4, " + CLIENT + ", " + CLOUDFLARE_EDGE)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Cloudflare por IPv6 también se salta")
    void cloudflare_por_ipv6_tambien_se_salta() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ", 2606:4700:10::6816:884b")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Sin cabecera, o con una cabecera solo de proxies, la IP es la de la conexión")
    void sin_cabecera_o_solo_de_proxies_la_ip_es_la_de_la_conexion() {
        assertThat(resolver.resolve(RENDER_PROXY, null)).isEqualTo(RENDER_PROXY);
        assertThat(resolver.resolve(RENDER_PROXY, " ")).isEqualTo(RENDER_PROXY);
        assertThat(resolver.resolve(RENDER_PROXY, CLOUDFLARE_EDGE + ", 10.0.0.1")).isEqualTo(RENDER_PROXY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "999.1.1.1", "no-es-una-ip", "1.2.3", "::zz", "", ":::", "1:2", "::1.2"})
    @DisplayName("Una entrada que no es una IP literal no se consulta en DNS: la IP es la de la conexión")
    void una_entrada_que_no_es_una_ip_literal_da_la_ip_de_la_conexion(String entry) {
        assertThat(resolver.resolve(RENDER_PROXY, entry + ", " + CLOUDFLARE_EDGE)).isEqualTo(RENDER_PROXY);
    }

    @Test
    @DisplayName("Un cliente IPv6 a través de Cloudflare es la IP")
    void un_cliente_ipv6_a_traves_de_cloudflare_es_la_ip() {
        assertThat(resolver.resolve(RENDER_PROXY, "2001:db8::1, " + CLOUDFLARE_EDGE)).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("Una entrada vacía en medio de la cabecera da la IP de la conexión")
    void una_entrada_vacia_en_medio_da_la_ip_de_la_conexion() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ",, " + CLOUDFLARE_EDGE)).isEqualTo(RENDER_PROXY);
    }

    @Test
    @DisplayName("Una IPv4 escrita como IPv6 de un proxy interno se reconoce como proxy")
    void una_ipv4_escrita_como_ipv6_de_un_proxy_interno_se_reconoce() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ", ::ffff:10.0.0.1")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Los espacios alrededor de cada entrada se ignoran")
    void los_espacios_alrededor_de_cada_entrada_se_ignoran() {
        assertThat(resolver.resolve(RENDER_PROXY, "  " + CLIENT + " ,  " + CLOUDFLARE_EDGE + "  ")).isEqualTo(CLIENT);
    }
}

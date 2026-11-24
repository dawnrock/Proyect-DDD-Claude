package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** IP del cliente detrás de los proxies de confianza (#133, ADR 0017): el proxy interno de Render y Cloudflare. */
class ClientIpResolverTests {

    private static final String RENDER_PROXY = "10.201.3.4";
    private static final String CLOUDFLARE_EDGE = "104.22.136.75";
    private static final String CLIENT = "46.6.116.139";

    private final ClientIpResolver resolver = new ClientIpResolver(List.of(
            "10.0.0.0/8", "127.0.0.0/8", "104.16.0.0/13", "2606:4700::/32"));

    @Test
    @DisplayName("Un cliente que no llega a través de un proxy de confianza es la IP; su X-Forwarded-For se ignora")
    void aDirectClientIgnoresXForwardedFor() {
        assertThat(resolver.resolve(CLIENT, "1.2.3.4")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("A través del proxy de Render y de Cloudflare, la IP es la del cliente")
    void throughRenderAndCloudflareTheIpIsTheClients() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ", " + CLOUDFLARE_EDGE)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Tras la válvula de Tomcat, que ya dejó la IP de Cloudflare como conexión, la IP sigue siendo la del cliente")
    void behindTheTomcatValveTheIpIsStillTheClients() {
        assertThat(resolver.resolve(CLOUDFLARE_EDGE, CLIENT)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Las direcciones que añade el propio cliente a X-Forwarded-For quedan a la izquierda y no cuentan")
    void addressesAddedByTheClientDoNotCount() {
        assertThat(resolver.resolve(RENDER_PROXY, "1.2.3.4, " + CLIENT + ", " + CLOUDFLARE_EDGE)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Cloudflare por IPv6 también se salta")
    void cloudflareOverIpv6IsAlsoSkipped() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ", 2606:4700:10::6816:884b")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Sin cabecera, o con una cabecera solo de proxies, la IP es la de la conexión")
    void withoutHeaderOrOnlyProxiesTheIpIsTheConnections() {
        assertThat(resolver.resolve(RENDER_PROXY, null)).isEqualTo(RENDER_PROXY);
        assertThat(resolver.resolve(RENDER_PROXY, " ")).isEqualTo(RENDER_PROXY);
        assertThat(resolver.resolve(RENDER_PROXY, CLOUDFLARE_EDGE + ", 10.0.0.1")).isEqualTo(RENDER_PROXY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "999.1.1.1", "no-es-una-ip", "1.2.3", "::zz", "", ":::", "1:2", "::1.2"})
    @DisplayName("Una entrada que no es una IP literal no se consulta en DNS: la IP es la de la conexión")
    void anEntryThatIsNotALiteralIpGivesTheConnectionIp(String entry) {
        assertThat(resolver.resolve(RENDER_PROXY, entry + ", " + CLOUDFLARE_EDGE)).isEqualTo(RENDER_PROXY);
    }

    @Test
    @DisplayName("Un cliente IPv6 a través de Cloudflare es la IP")
    void anIpv6ClientThroughCloudflareIsTheIp() {
        assertThat(resolver.resolve(RENDER_PROXY, "2001:db8::1, " + CLOUDFLARE_EDGE)).isEqualTo("2001:db8::1");
    }

    @Test
    @DisplayName("Una entrada vacía en medio de la cabecera da la IP de la conexión")
    void anEmptyEntryInTheMiddleGivesTheConnectionIp() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ",, " + CLOUDFLARE_EDGE)).isEqualTo(RENDER_PROXY);
    }

    @Test
    @DisplayName("Una IPv4 escrita como IPv6 de un proxy interno se reconoce como proxy")
    void anIpv4WrittenAsIpv6FromAnInternalProxyIsRecognised() {
        assertThat(resolver.resolve(RENDER_PROXY, CLIENT + ", ::ffff:10.0.0.1")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Los espacios alrededor de cada entrada se ignoran")
    void spacesAroundEachEntryAreIgnored() {
        assertThat(resolver.resolve(RENDER_PROXY, "  " + CLIENT + " ,  " + CLOUDFLARE_EDGE + "  ")).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Desde una petición, varias cabeceras X-Forwarded-For se unen en orden: la del cliente primero, la del proxy después (#131)")
    void fromARequestTheHeadersAreJoinedInOrder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(RENDER_PROXY);
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Forwarded-For", CLIENT + ", " + CLOUDFLARE_EDGE);

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
    }

    @Test
    @DisplayName("Desde una petición sin X-Forwarded-For, la IP es la de la conexión (#131)")
    void fromARequestWithoutHeaderTheIpIsTheConnections() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT);

        assertThat(resolver.resolve(request)).isEqualTo(CLIENT);
    }
}

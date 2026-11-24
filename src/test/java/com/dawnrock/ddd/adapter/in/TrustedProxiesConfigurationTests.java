package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * La lista de proxies de confianza de {@code application.properties} (#133, ADR 0017): el proxy interno de Render y los
 * rangos oficiales de Cloudflare (https://www.cloudflare.com/ips-v4 y /ips-v6).
 */
class TrustedProxiesConfigurationTests {

    private static ClientIpResolver configuredResolver() throws IOException {
        Properties file = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        TrustedProxiesProperties properties = new Binder(new MapConfigurationPropertySource(file))
                .bind("app.security", TrustedProxiesProperties.class).orElse(new TrustedProxiesProperties(null));
        return new ClientIpResolver(properties.trustedProxies());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "104.22.136.75", "173.245.48.1", "103.21.244.1", "103.22.200.1", "103.31.4.1", "141.101.64.1",
            "108.162.192.1", "190.93.240.1", "188.114.96.1", "197.234.240.1", "198.41.128.1", "162.158.0.1",
            "104.24.0.1", "172.64.0.1", "131.0.72.1",
            "2400:cb00::1", "2606:4700::1", "2803:f800::1", "2405:b500::1", "2405:8100::1", "2a06:98c0::1", "2c0f:f248::1",
            "10.201.3.4", "127.0.0.1"})
    @DisplayName("Los nodos de Cloudflare y el proxy interno de Render son proxies de confianza")
    void cloudflareNodesAndTheRenderProxyAreTrusted(String proxy) throws IOException {
        assertThat(configuredResolver().resolve("10.201.3.4", "46.6.116.139, " + proxy)).isEqualTo("46.6.116.139");
    }

    @ParameterizedTest
    @ValueSource(strings = {"46.6.116.139", "8.8.8.8", "2001:db8::1"})
    @DisplayName("Una IP pública que no es de Cloudflare no es un proxy de confianza")
    void aPublicIpNotFromCloudflareIsNotTrusted(String client) throws IOException {
        assertThat(configuredResolver().resolve("10.201.3.4", "203.0.113.9, " + client)).isEqualTo(client);
    }
}

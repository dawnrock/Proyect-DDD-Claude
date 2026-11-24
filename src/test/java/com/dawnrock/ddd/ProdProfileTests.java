package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Perfil {@code prod} (#150, ADR 0019): la base de datos sale solo de las variables de entorno {@code SPRING_DATASOURCE_*}
 * (Neon en Render). Sin ellas la aplicación no arranca, en lugar de caer en silencio en la H2 en memoria.
 */
class ProdProfileTests {

    private static SpringApplicationBuilder prod() {
        return new SpringApplicationBuilder(DddLabApplication.class)
                .profiles("prod")
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0")
                .logStartupInfo(false);
    }

    @Test
    @DisplayName("Con el perfil prod y sin SPRING_DATASOURCE_URL la aplicación no arranca")
    void withTheProdProfileAndNoDatabaseUrlTheApplicationDoesNotStart() {
        // El marcador ${SPRING_DATASOURCE_URL} queda sin resolver y no es una URL de JDBC: falla al crear la conexión
        assertThatThrownBy(() -> prod().run().close())
                .hasStackTraceContaining("'url' must start with \"jdbc\"");
    }

    @Test
    @DisplayName("Con el perfil prod la aplicación usa la base que dicen las variables SPRING_DATASOURCE_*")
    void withTheProdProfileTheApplicationUsesTheDatabaseFromTheVariables() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (ConfigurableApplicationContext context = prod().run("--SPRING_DATASOURCE_URL=" + url,
                "--SPRING_DATASOURCE_USERNAME=sa", "--SPRING_DATASOURCE_PASSWORD=")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(url);
            assertThat(context.getEnvironment().getProperty("spring.h2.console.enabled")).isEqualTo("false");
        }
    }

    @Test
    @DisplayName("Con el perfil prod la conexión tiene tiempos de espera aunque la URL no los lleve")
    void withTheProdProfileTheConnectionHasTimeoutsEvenIfTheUrlHasNone() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (ConfigurableApplicationContext context = prod().run("--SPRING_DATASOURCE_URL=" + url,
                "--SPRING_DATASOURCE_USERNAME=sa", "--SPRING_DATASOURCE_PASSWORD=")) {
            HikariDataSource pool = context.getBean(HikariDataSource.class);
            // Segundos, como los espera el driver de PostgreSQL (#156)
            assertThat(pool.getDataSourceProperties())
                    .containsEntry("connectTimeout", "15")
                    .containsEntry("loginTimeout", "30")
                    .containsEntry("socketTimeout", "120");
            assertThat(pool.getConnectionTimeout()).isEqualTo(30_000);
            assertThat(pool.getInitializationFailTimeout()).isEqualTo(1);
            assertThat(pool.getKeepaliveTime()).isEqualTo(120_000);
        }
    }

    @Test
    @DisplayName("Con el perfil prod una base que no contesta hace fallar el arranque en segundos, sin colgarlo")
    void withTheProdProfileAnUnresponsiveDatabaseFailsTheStartupWithoutHanging() throws IOException {
        // Acepta la conexión TCP y nunca contesta, como el cómputo de Neon que no despertaba el 2026-11-23 (#156)
        try (ServerSocket silentDatabase = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            String url = "jdbc:postgresql://127.0.0.1:" + silentDatabase.getLocalPort() + "/monabank";
            // Solo se acorta loginTimeout (30 s en el perfil) para que el test sea rápido; la URL no lleva tiempos
            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertThatThrownBy(() -> prod()
                            .run("--SPRING_DATASOURCE_URL=" + url, "--SPRING_DATASOURCE_USERNAME=monabank",
                                    "--SPRING_DATASOURCE_PASSWORD=secreto",
                                    "--spring.datasource.hikari.data-source-properties.loginTimeout=2")
                            .close())
                    .hasStackTraceContaining("Connection attempt timed out"));
        }
    }
}

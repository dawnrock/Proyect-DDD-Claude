package com.pronoide.ddd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void con_el_perfil_prod_y_sin_url_de_la_base_la_aplicacion_no_arranca() {
        // El marcador ${SPRING_DATASOURCE_URL} queda sin resolver y no es una URL de JDBC: falla al crear la conexión
        assertThatThrownBy(() -> prod().run().close())
                .hasStackTraceContaining("'url' must start with \"jdbc\"");
    }

    @Test
    @DisplayName("Con el perfil prod la aplicación usa la base que dicen las variables SPRING_DATASOURCE_*")
    void con_el_perfil_prod_la_aplicacion_usa_la_base_de_las_variables() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (ConfigurableApplicationContext context = prod().run("--SPRING_DATASOURCE_URL=" + url,
                "--SPRING_DATASOURCE_USERNAME=sa", "--SPRING_DATASOURCE_PASSWORD=")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(url);
            assertThat(context.getEnvironment().getProperty("spring.h2.console.enabled")).isEqualTo("false");
        }
    }
}

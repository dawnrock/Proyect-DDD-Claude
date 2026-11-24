package com.dawnrock.ddd.adapter.out.event;

import java.util.function.Supplier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base de datos de los tests del outbox (#165): un contexto mínimo de Spring (base H2, Flyway, JPA y el repositorio de
 * Spring Data de este paquete), sin el resto de la aplicación. Mismo patrón que {@code JpaTestDatabase} de
 * {@code adapter.out.persistence}.
 */
final class OutboxTestDatabase {

    private static ConfigurableApplicationContext context;

    private OutboxTestDatabase() {
    }

    static synchronized <T> T bean(Class<T> type) {
        if (context == null) {
            context = new SpringApplicationBuilder(Config.class)
                    .web(WebApplicationType.NONE)
                    .logStartupInfo(false)
                    .run();
        }
        return context.getBean(type);
    }

    /** Ejecuta el trabajo en una transacción, como lo haría la unidad de trabajo. */
    static <T> T inTransaction(Supplier<T> work) {
        return bean(TransactionTemplate.class).execute(status -> work.get());
    }

    /** Vacía el outbox: cada test empieza sin eventos. */
    static void clear() {
        bean(JdbcTemplate.class).update("delete from domain_event_outbox");
    }

    /** Sin {@code @Configuration}: el escaneo de componentes de la aplicación no debe encontrarla (ver JpaTestDatabase). */
    @AutoConfigurationPackage
    @AutoConfigureDataJpa
    static class Config {
    }
}

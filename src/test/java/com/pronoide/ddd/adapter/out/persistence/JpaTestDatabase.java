package com.pronoide.ddd.adapter.out.persistence;

import java.util.function.Supplier;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base de datos de los tests de los adaptadores JPA (#150): un contexto mínimo de Spring (base H2 de la configuración,
 * Flyway, JPA y los repositorios de Spring Data de este paquete), sin el resto de la aplicación, compartido por todos
 * ellos. No se usa {@code @DataJpaTest} porque los contratos de los puertos agrupan sus tests con {@code @Nested}, y
 * Spring construye el contexto de una clase anidada a partir de la clase que la declara (el contrato, que no conoce
 * Spring).
 */
final class JpaTestDatabase {

    private static ConfigurableApplicationContext context;

    private JpaTestDatabase() {
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

    static void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    /** Vacía todas las tablas de datos: cada test empieza con la base vacía. */
    static void clear() {
        JdbcTemplate jdbc = bean(JdbcTemplate.class);
        for (String table : new String[] {"account_transaction", "checking_account", "customer", "customer_username",
                "account_owner", "authorities", "users"}) {
            jdbc.update("delete from " + table);
        }
    }

    /**
     * Sin {@code @Configuration} a propósito: así el escaneo de componentes de la aplicación no la encuentra en los tests
     * que arrancan el contexto completo (registraría dos veces los repositorios de Spring Data).
     */
    @AutoConfigurationPackage
    @AutoConfigureDataJpa
    static class Config {
    }
}

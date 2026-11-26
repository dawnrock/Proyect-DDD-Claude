package com.dawnrock.ddd.adapter.in;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** La implementación de JDBC sobre H2 en modo PostgreSQL, con el esquema real de Flyway (incluida la clave foránea). */
class JdbcExternalIdentitiesTests extends ExternalIdentitiesContract {

    private static SingleConnectionDataSource dataSource;

    @BeforeAll
    static void migrateDatabase() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:extid-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @Override
    protected ExternalIdentities createIdentities() {
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("delete from external_identity").update();
        jdbc.sql("delete from authorities").update();
        jdbc.sql("delete from users").update();
        return new JdbcExternalIdentities(jdbc);
    }

    @Override
    protected void createUser(String username) {
        JdbcClient.create(dataSource).sql("insert into users (username, password, enabled) values (?, ?, true)")
                .params(username, "{noop}x").update();
    }
}

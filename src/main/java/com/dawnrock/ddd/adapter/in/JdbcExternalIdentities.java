package com.dawnrock.ddd.adapter.in;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * {@link ExternalIdentities} sobre la tabla {@code external_identity} (Flyway {@code V3}, ADR 0024): clave
 * {@code (provider, subject)}, única {@code (provider, username)} y clave foránea a {@code users}. Las restricciones
 * de la base de datos son la última defensa frente a vinculaciones simultáneas.
 */
public class JdbcExternalIdentities implements ExternalIdentities {

    private final JdbcClient jdbc;

    public JdbcExternalIdentities(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> usernameOf(String provider, String subject) {
        return jdbc.sql("select username from external_identity where provider = ? and subject = ?")
                .params(provider, subject)
                .query(String.class)
                .optional();
    }

    @Override
    public void link(String provider, String subject, String username) {
        Integer users = jdbc.sql("select count(*) from users where username = ?")
                .params(username).query(Integer.class).single();
        if (users == 0) {
            throw new IllegalArgumentException("No existe el usuario " + username);
        }
        Optional<String> current = usernameOf(provider, subject);
        if (current.isPresent()) {
            if (current.get().equals(username)) {
                return;
            }
            throw new IdentityAlreadyLinkedException(provider, username);
        }
        try {
            jdbc.sql("insert into external_identity (provider, subject, username) values (?, ?, ?)")
                    .params(provider, subject, username)
                    .update();
        } catch (DuplicateKeyException e) {
            // El usuario ya tiene otra identidad del proveedor, o alguien vinculó esta justo antes
            throw new IdentityAlreadyLinkedException(provider, username);
        }
    }
}

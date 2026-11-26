-- Identidades de proveedores externos (Auth0) vinculadas a un usuario local (#196, ADR 0024). Vale para PostgreSQL
-- (Neon) y para H2 en modo PostgreSQL. La clave foránea impide borrar un usuario mientras tenga una identidad vinculada.
create table external_identity (
    provider varchar(20)  not null,
    subject  varchar(255) not null,
    username varchar(50)  not null,
    constraint pk_external_identity primary key (provider, subject),
    constraint uk_external_identity_provider_username unique (provider, username),
    constraint fk_external_identity_users foreign key (username) references users (username)
);

-- Esquema inicial de la persistencia real (#150, ADR 0019). Vale para PostgreSQL (Neon) y para H2 en modo PostgreSQL.

-- Agregado CheckingAccount: el titular va embebido en la cuenta, los movimientos en su propia tabla.
create table checking_account (
    id                  uuid           not null primary key,
    account_number      varchar(24)    not null,
    holder_id           uuid           not null,
    holder_tax_id       varchar(9)     not null,
    holder_full_name    varchar(1000)  not null,
    holder_segment      varchar(20)    not null,
    balance             numeric(19, 2) not null,
    currency            varchar(3)     not null,
    next_segment_review timestamp(6) with time zone,
    version             bigint         not null,
    constraint uk_checking_account_number unique (account_number)
);

create table account_transaction (
    id           uuid           not null primary key,
    account_id   uuid           not null,
    position     integer        not null,
    type         varchar(20)    not null,
    amount       numeric(19, 2) not null,
    currency     varchar(3)     not null,
    occurred_on  timestamp(6) with time zone not null,
    counterpart  varchar(24),
    status       varchar(20)    not null,
    constraint fk_account_transaction_account foreign key (account_id) references checking_account (id),
    constraint uk_account_transaction_position unique (account_id, position)
);

-- Agregado Customer: un documento por cliente (R30).
create table customer (
    id        uuid          not null primary key,
    tax_id    varchar(9)    not null,
    full_name varchar(1000) not null,
    email     varchar(320)  not null,
    constraint uk_customer_tax_id unique (tax_id)
);

-- Puertos de application (ADR 0008): la identidad no entra en el dominio.
create table customer_username (
    tax_id   varchar(9)  not null primary key,
    username varchar(50) not null,
    constraint uk_customer_username unique (username)
);

create table account_owner (
    account_id uuid        not null primary key,
    username   varchar(50) not null
);

create index ix_account_owner_username on account_owner (username);

-- Numeración de cuentas: el número secuencial del IBAN.
create sequence account_number_seq start with 1 increment by 1;

-- Usuarios de Spring Security (esquema estándar de JdbcUserDetailsManager).
create table users (
    username varchar(50)  not null primary key,
    password varchar(500) not null,
    enabled  boolean      not null
);

create table authorities (
    username  varchar(50) not null,
    authority varchar(50) not null,
    constraint fk_authorities_users foreign key (username) references users (username),
    constraint uk_authorities_username_authority unique (username, authority)
);

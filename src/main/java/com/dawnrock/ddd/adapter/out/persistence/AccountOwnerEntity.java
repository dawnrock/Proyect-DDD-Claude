package com.dawnrock.ddd.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la propiedad de una cuenta: qué usuario es su dueño (#150, ADR 0008). */
@Entity
@Table(name = "account_owner")
class AccountOwnerEntity {

    @Id
    UUID accountId;
    String username;

    protected AccountOwnerEntity() {
    }

    AccountOwnerEntity(UUID accountId, String username) {
        this.accountId = accountId;
        this.username = username;
    }
}

package com.pronoide.ddd.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la relación cliente-usuario (#150): un usuario por cliente y un cliente por usuario (R30). */
@Entity
@Table(name = "customer_username")
class CustomerUsernameEntity {

    @Id
    String taxId;
    String username;

    protected CustomerUsernameEntity() {
    }

    CustomerUsernameEntity(String taxId, String username) {
        this.taxId = taxId;
        this.username = username;
    }
}

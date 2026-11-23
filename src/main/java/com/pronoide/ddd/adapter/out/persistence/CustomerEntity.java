package com.pronoide.ddd.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de un cliente (#150). El documento es único en la tabla (R30). */
@Entity
@Table(name = "customer")
class CustomerEntity {

    @Id
    UUID id;
    String taxId;
    String fullName;
    String email;

    protected CustomerEntity() {
    }

    CustomerEntity(UUID id, String taxId, String fullName, String email) {
        this.id = id;
        this.taxId = taxId;
        this.fullName = fullName;
        this.email = email;
    }
}

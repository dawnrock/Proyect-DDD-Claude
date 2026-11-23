package com.pronoide.ddd.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Fila de un movimiento de una cuenta (#150). {@code position} guarda el orden en que se registró. */
@Entity
@Table(name = "account_transaction")
class TransactionEntity {

    @Id
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    CheckingAccountEntity account;

    int position;
    String type;
    BigDecimal amount;
    String currency;
    Instant occurredOn;
    String counterpart;
    String status;

    protected TransactionEntity() {
    }

    TransactionEntity(UUID id) {
        this.id = id;
    }
}

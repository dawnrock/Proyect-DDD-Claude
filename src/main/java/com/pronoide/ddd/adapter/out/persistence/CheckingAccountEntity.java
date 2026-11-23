package com.pronoide.ddd.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fila de una cuenta (#150, ADR 0019). No es el agregado: {@link CheckingAccountMapper} traduce entre las dos. El
 * titular va embebido en la cuenta porque es parte del agregado; los movimientos, en su propia tabla y en orden.
 */
@Entity
@Table(name = "checking_account")
class CheckingAccountEntity {

    @Id
    UUID id;
    String accountNumber;
    UUID holderId;
    String holderTaxId;
    String holderFullName;
    String holderSegment;
    BigDecimal balance;
    String currency;
    Instant nextSegmentReview;

    /** Red de seguridad frente a escrituras perdidas; la unidad de trabajo ya serializa los casos de uso. */
    @Version
    long version;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    List<TransactionEntity> transactions = new ArrayList<>();

    protected CheckingAccountEntity() {
    }

    CheckingAccountEntity(UUID id) {
        this.id = id;
    }
}

package com.pronoide.ddd.domain.model;

import java.util.Objects;
import java.util.UUID;

public class AccountHolder {

    private final UUID id;
    private String fullName;
    private CustomerSegment segment;

    private AccountHolder(UUID id, String fullName, CustomerSegment segment) {
        this.id = id;
        this.fullName = fullName;
        this.segment = segment;
    }

    /** Da de alta a un titular. Todo titular empieza como minorista: el segmento lo decide el saldo de su cuenta. */
    public static AccountHolder register(String fullName) {
        return new AccountHolder(UUID.randomUUID(), requireName(fullName), CustomerSegment.RETAIL);
    }

    public void rename(String fullName) {
        this.fullName = requireName(fullName);
    }

    public void promoteToHighNetWorth() {
        this.segment = CustomerSegment.HIGH_NET_WORTH;
    }

    public void demoteToRetail() {
        this.segment = CustomerSegment.RETAIL;
    }

    public boolean isHighNetWorth() {
        return segment == CustomerSegment.HIGH_NET_WORTH;
    }

    public UUID id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public CustomerSegment segment() {
        return segment;
    }

    private static String requireName(String fullName) {
        Objects.requireNonNull(fullName, "El nombre del titular es obligatorio");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre del titular no puede estar vacío");
        }
        return fullName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AccountHolder that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s [%s]".formatted(fullName, segment);
    }
}

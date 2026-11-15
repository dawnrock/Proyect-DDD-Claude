package com.pronoide.ddd.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Cliente de Mon-A Bank: la persona que se identifica por su documento y puede tener varias cuentas. No conoce a los
 * usuarios (el dominio no conoce a nadie): la relación cliente-usuario vive en la persistencia. Que dos clientes no
 * compartan documento (R29) lo comprueban los casos de uso contra el repositorio.
 */
public class Customer {

    private final UUID id;
    private final TaxId taxId;
    private final String fullName;
    private final EmailAddress email;

    private Customer(UUID id, String fullName, TaxId taxId, EmailAddress email) {
        this.id = id;
        this.fullName = fullName;
        this.taxId = taxId;
        this.email = email;
    }

    public static Customer register(String fullName, TaxId taxId, EmailAddress email) {
        Objects.requireNonNull(fullName, "El nombre del cliente es obligatorio");
        if (fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente no puede estar vacío");
        }
        return new Customer(UUID.randomUUID(), fullName.strip(),
                Objects.requireNonNull(taxId, "El documento del cliente es obligatorio"),
                Objects.requireNonNull(email, "El correo del cliente es obligatorio"));
    }

    public UUID id() {
        return id;
    }

    public TaxId taxId() {
        return taxId;
    }

    public String fullName() {
        return fullName;
    }

    public EmailAddress email() {
        return email;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Customer that)) {
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
        return "%s [%s]".formatted(fullName, taxId);
    }
}

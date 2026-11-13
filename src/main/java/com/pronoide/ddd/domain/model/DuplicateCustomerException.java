package com.pronoide.ddd.domain.model;

/** Ya hay un cliente con ese documento (R29): un cliente no se da de alta dos veces, aunque cambie el correo. */
public class DuplicateCustomerException extends DomainException {

    public DuplicateCustomerException(TaxId taxId) {
        super("Ya eres cliente de Mon-A Bank: ya hay un cliente con el documento %s. Entra con tu usuario"
                .formatted(taxId));
    }
}

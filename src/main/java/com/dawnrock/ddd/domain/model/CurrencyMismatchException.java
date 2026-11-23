package com.dawnrock.ddd.domain.model;

import java.util.Currency;


public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency expected, Currency found) {
        super("La operación esperaba importes en %s, pero ha recibido %s"
                .formatted(expected.getCurrencyCode(), found.getCurrencyCode()));
    }
}

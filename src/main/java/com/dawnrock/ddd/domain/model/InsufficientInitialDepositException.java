package com.dawnrock.ddd.domain.model;

public class InsufficientInitialDepositException extends DomainException {

    public InsufficientInitialDepositException(MonetaryAmount deposit, 
                                              MonetaryAmount minimum) {
        super("El depósito inicial de %s no alcanza el mínimo exigido de %s"
                                        .formatted(deposit, minimum));
    }
}

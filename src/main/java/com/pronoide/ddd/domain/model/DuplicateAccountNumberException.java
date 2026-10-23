package com.pronoide.ddd.domain.model;

public class DuplicateAccountNumberException extends DomainException {

    public DuplicateAccountNumberException(AccountNumber accountNumber) {
        super("Ya existe una cuenta con el número %s".formatted(accountNumber));
    }
}

package com.pronoide.ddd.domain.model;

public class SameAccountTransferException extends DomainException {

    public SameAccountTransferException(AccountNumber accountNumber) {
        super("No se puede transferir a la misma cuenta: %s".formatted(accountNumber));
    }
}

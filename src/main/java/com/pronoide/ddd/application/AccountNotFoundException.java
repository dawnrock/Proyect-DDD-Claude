package com.pronoide.ddd.application;

import java.util.UUID;

/** No existe ninguna cuenta con el id pedido. La presentación lo traduce a 404. */
public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException(UUID id) {
        super("No existe la cuenta %s".formatted(id));
    }
}

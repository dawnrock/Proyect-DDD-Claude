package com.pronoide.ddd.application;

/** Quien pide abrir otra cuenta no es un cliente registrado (un administrador o un usuario sin alta de cliente). */
public class NotACustomerException extends OperationNotAllowedException {

    public NotACustomerException(String username) {
        super("El usuario %s no es cliente de Mon-A Bank: no puede abrir otra cuenta".formatted(username));
    }
}

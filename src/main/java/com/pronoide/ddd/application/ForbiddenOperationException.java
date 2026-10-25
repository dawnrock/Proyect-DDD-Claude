package com.pronoide.ddd.application;

/** Un usuario sin rol de administrador intenta una operación reservada (abrir cuentas). La presentación da 403. */
public class ForbiddenOperationException extends OperationNotAllowedException {

    public ForbiddenOperationException(String operation) {
        super("Solo un administrador puede %s".formatted(operation));
    }
}

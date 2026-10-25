package com.pronoide.ddd.application;

/**
 * No existe ninguna cuenta con el número de destino de una transferencia. La API lo traduce a 404, como cualquier
 * {@link NotFoundException}; la web lo muestra en el formulario de transferencia en lugar de la página 404, porque es
 * un dato que el usuario ha escrito (a diferencia de la cuenta de origen, que es la página en la que está).
 */
public class TargetAccountNotFoundException extends NotFoundException {

    public TargetAccountNotFoundException(String accountNumber) {
        super("No existe ninguna cuenta con el número %s".formatted(accountNumber));
    }
}

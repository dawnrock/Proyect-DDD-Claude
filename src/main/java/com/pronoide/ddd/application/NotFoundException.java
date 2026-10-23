package com.pronoide.ddd.application;

/**
 * Base de los errores de aplicación "el recurso pedido no existe" (por ejemplo, una cuenta con un id
 * desconocido). La presentación los traduce a 404. No es una regla de negocio: el dominio no sabe si
 * algo existe, eso lo decide el caso de uso al consultar la persistencia.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}

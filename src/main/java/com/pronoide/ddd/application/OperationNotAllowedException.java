package com.pronoide.ddd.application;

/**
 * Base de los errores de aplicación "quien pide no puede hacer esto" (falta el rol necesario). La presentación los
 * traduce a 403. No confundir con la {@code AccessDeniedException} de Spring Security.
 *
 * <p>Una cuenta ajena no lo es: se trata como inexistente ({@link AccountNotFoundException}, 404) para no revelar que
 * existe.
 */
public abstract class OperationNotAllowedException extends RuntimeException {

    protected OperationNotAllowedException(String message) {
        super(message);
    }
}

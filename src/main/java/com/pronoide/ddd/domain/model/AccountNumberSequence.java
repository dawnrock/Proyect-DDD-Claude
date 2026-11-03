package com.pronoide.ddd.domain.model;

/**
 * Puerto de salida: la numeración de cuentas. Lo que la factoría necesita para dar un IBAN nuevo a cada cuenta, sin
 * saber quién lo genera. Lo implementa un adaptador de {@code adapter.out.persistence}.
 */
public interface AccountNumberSequence {

    /** Un número de cuenta nuevo, que no se ha entregado antes. */
    AccountNumber next();
}

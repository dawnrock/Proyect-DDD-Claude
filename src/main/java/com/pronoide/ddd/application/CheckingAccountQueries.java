package com.pronoide.ddd.application;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada de los casos de uso que consultan cuentas: listado y detalle. Los adaptadores de entrada dependen de
 * este puerto y no de la clase que lo implementa ({@code CheckingAccountService}). Un usuario solo ve sus cuentas; un
 * administrador, todas (ADR 0008).
 */
public interface CheckingAccountQueries {

    /** Todas las cuentas para un administrador; las suyas para un usuario. Ordenadas por número de cuenta. */
    List<AccountSummary> listAccounts(Caller caller);

    /**
     * Detalle de una cuenta, con sus movimientos.
     *
     * @throws AccountNotFoundException si no existe o no es de quien lo pide (y no es administrador)
     */
    AccountDetails findAccount(Caller caller, UUID id);
}

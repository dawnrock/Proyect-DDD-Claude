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

    /**
     * Si el usuario ya es propietario de algún cliente (R30), aunque todavía no tenga credenciales: así nadie se da de
     * alta con el usuario que el administrador asignó a un cliente. No mira los usuarios de Spring Security, que la
     * aplicación no conoce; eso lo hace {@code adapter.in.CustomerRegistration}.
     */
    boolean isUsernameTaken(String username);
}

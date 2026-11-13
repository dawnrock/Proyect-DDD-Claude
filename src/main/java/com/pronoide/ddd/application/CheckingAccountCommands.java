package com.pronoide.ddd.application;

/**
 * Puerto de entrada de los casos de uso que cambian cuentas: abrir, ingresar, retirar y transferir. Los adaptadores de
 * entrada (REST, web, demo) dependen de este puerto y no de la clase que lo implementa ({@code CheckingAccountService}).
 * Cada caso de uso recibe quién lo pide ({@link Caller}) y aplica la propiedad de las cuentas (ADR 0008).
 */
public interface CheckingAccountCommands {

    /**
     * Abre una cuenta con su depósito inicial para {@code command.ownerUsername()}, que será su propietario. Solo un
     * administrador.
     *
     * @throws ForbiddenOperationException si quien lo pide no es administrador
     * @throws IllegalArgumentException si el command no indica el usuario propietario
     * @throws com.pronoide.ddd.domain.model.DuplicateAccountNumberException si el número de cuenta ya está en uso
     * @throws com.pronoide.ddd.domain.model.InsufficientInitialDepositException si el depósito no llega al mínimo
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el depósito no está en euros
     */
    AccountSummary openAccount(Caller caller, OpenAccountCommand command);

    /**
     * Ingresa un importe en la cuenta y la guarda. Contrato: {@code docs/contracts/deposits-withdrawals.md}.
     *
     * @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador)
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    TransactionSummary deposit(Caller caller, DepositCommand command);

    /**
     * Intenta retirar un importe de la cuenta y la guarda. Una retirada que dejaría la cuenta por debajo de lo
     * permitido no es un error: se devuelve el movimiento con estado {@code REJECTED} y el saldo no cambia.
     *
     * @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador)
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    TransactionSummary withdraw(Caller caller, WithdrawCommand command);

    /**
     * Transfiere desde una cuenta de quien lo pide a cualquier cuenta existente, indicada por su número
     * ({@code command.targetAccountNumber()}, que se normaliza como {@code AccountNumber}), y guarda las dos. Una
     * transferencia que la cuenta de origen no permite no es un error: se devuelve con estado {@code REJECTED} y ningún
     * saldo cambia. Contratos: {@code docs/contracts/money-transfers.md} y {@code login-roles.md}.
     *
     * @throws AccountNotFoundException si el origen no existe o no es de quien lo pide (y no es administrador)
     * @throws TargetAccountNotFoundException si no existe ninguna cuenta con el número de destino
     * @throws com.pronoide.ddd.domain.model.SameAccountTransferException si origen y destino son la misma cuenta
     * @throws com.pronoide.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de las cuentas
     */
    TransferSummary transfer(Caller caller, TransferCommand command);

    /**
     * Revisa el segmento de todas las cuentas a las que les toca: vuelve a verificar los ingresos del titular y lo
     * devuelve a minorista si ya no le corresponde el alto patrimonio. Es un proceso interno, sin {@link Caller}: lo
     * dispara la tarea programada.
     *
     * @return cuántos titulares han vuelto a minorista
     */
    int reviewSegments();
}

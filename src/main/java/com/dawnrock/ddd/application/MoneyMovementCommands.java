package com.dawnrock.ddd.application;

/**
 * Puerto de entrada de los movimientos de dinero: ingresar, retirar y transferir (#174). Los adaptadores de entrada
 * (REST, web, demo) dependen de este puerto y no de la clase que lo implementa ({@code MoneyMovementService}). Cada caso
 * de uso recibe quién lo pide ({@link Caller}) y aplica la propiedad de las cuentas (ADR 0008).
 */
public interface MoneyMovementCommands {

    /**
     * Ingresa un importe en la cuenta y la guarda. Contrato: {@code docs/contracts/deposits-withdrawals.md}.
     *
     * @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador)
     * @throws com.dawnrock.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
     */
    TransactionSummary deposit(Caller caller, DepositCommand command);

    /**
     * Intenta retirar un importe de la cuenta y la guarda. Una retirada que dejaría la cuenta por debajo de lo
     * permitido no es un error: se devuelve el movimiento con estado {@code REJECTED} y el saldo no cambia.
     *
     * @throws AccountNotFoundException si la cuenta no existe o no es de quien lo pide (y no es administrador)
     * @throws com.dawnrock.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de la cuenta
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
     * @throws com.dawnrock.ddd.domain.model.SameAccountTransferException si origen y destino son la misma cuenta
     * @throws com.dawnrock.ddd.domain.model.CurrencyMismatchException si el importe no está en la divisa de las cuentas
     */
    TransferSummary transfer(Caller caller, TransferCommand command);
}

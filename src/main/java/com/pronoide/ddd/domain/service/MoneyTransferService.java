package com.pronoide.ddd.domain.service;

import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.SameAccountTransferException;
import com.pronoide.ddd.domain.model.Transaction;

/**
 * Servicio de dominio de las transferencias entre dos cuentas. Sin estado y sin dependencias de infraestructura: solo
 * coordina las reglas de {@code CheckingAccount}, no añade reglas de saldo propias.
 */
public class MoneyTransferService {

    /**
     * Intenta retirar el importe del origen y, solo si la retirada se aprueba, lo ingresa en el destino.
     *
     * @return la retirada registrada en el origen; su estado es el resultado de la transferencia
     */
    public Transaction transfer(CheckingAccount source, CheckingAccount target, MonetaryAmount amount) {
        if (source.equals(target)) {
            throw new SameAccountTransferException(source.accountNumber());
        }
        Transaction withdrawal = source.tryWithdraw(amount);
        if (withdrawal.isApproved()) {
            target.deposit(amount);
        }
        return withdrawal;
    }
}

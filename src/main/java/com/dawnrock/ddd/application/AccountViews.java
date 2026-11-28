package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Currency;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.Transaction;

/**
 * Traduce entre el dominio y los commands y vistas de {@code application}: los importes de los commands a
 * {@link MonetaryAmount} y las cuentas y movimientos a sus vistas. Lo comparten los servicios de aplicación (#174);
 * no tiene estado.
 */
final class AccountViews {

    private AccountViews() {
    }

    static MonetaryAmount toAmount(BigDecimal amount, String currency) {
        return new MonetaryAmount(amount, Currency.getInstance(currency));
    }

    static AccountSummary toSummary(CheckingAccount account) {
        return new AccountSummary(account.id(), account.accountNumber().value(), account.primaryHolder().fullName(),
                account.primaryHolder().segment().name(), account.currentBalance().amount(),
                account.currentBalance().currency().getCurrencyCode());
    }

    /** El detalle de la cuenta; el correo del titular sale de sus datos de cliente (vacío si no los tiene). */
    static AccountDetails toDetails(CheckingAccount account, String holderEmail) {
        return new AccountDetails(account.id(), account.accountNumber().value(), account.primaryHolder().fullName(),
                account.primaryHolder().taxId().value(), holderEmail, account.primaryHolder().segment().name(),
                account.currentBalance().amount(), account.currentBalance().currency().getCurrencyCode(),
                account.transactions().stream().map(AccountViews::toTransaction).toList());
    }

    static TransactionSummary toTransaction(Transaction transaction) {
        return new TransactionSummary(transaction.id(), transaction.type().name(), transaction.amount().amount(),
                transaction.amount().currency().getCurrencyCode(), transaction.occurredOn(),
                transaction.status().name(), transaction.counterpart().map(AccountNumber::value));
    }

    /** La transferencia vista desde el origen: el movimiento de retirada da importe, fecha y estado. */
    static TransferSummary toTransfer(CheckingAccount source, CheckingAccount target, Transaction withdrawal) {
        return new TransferSummary(source.id(), source.accountNumber().value(), target.id(),
                target.accountNumber().value(), withdrawal.amount().amount(),
                withdrawal.amount().currency().getCurrencyCode(), withdrawal.occurredOn(), withdrawal.status().name());
    }
}

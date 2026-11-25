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
        return AccountSummary.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
                .segment(account.primaryHolder().segment().name())
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .build();
    }

    /** El detalle de la cuenta; el correo del titular sale de sus datos de cliente (vacío si no los tiene). */
    static AccountDetails toDetails(CheckingAccount account, String holderEmail) {
        return AccountDetails.builder()
                .id(account.id())
                .accountNumber(account.accountNumber().value())
                .holderName(account.primaryHolder().fullName())
                .holderTaxId(account.primaryHolder().taxId().value())
                .holderEmail(holderEmail)
                .segment(account.primaryHolder().segment().name())
                .balance(account.currentBalance().amount())
                .currency(account.currentBalance().currency().getCurrencyCode())
                .transactions(account.transactions().stream().map(AccountViews::toTransaction).toList())
                .build();
    }

    static TransactionSummary toTransaction(Transaction transaction) {
        return TransactionSummary.builder()
                .id(transaction.id())
                .type(transaction.type().name())
                .amount(transaction.amount().amount())
                .currency(transaction.amount().currency().getCurrencyCode())
                .occurredOn(transaction.occurredOn())
                .status(transaction.status().name())
                .counterpartAccountNumber(transaction.counterpart().map(AccountNumber::value))
                .build();
    }

    /** La transferencia vista desde el origen: el movimiento de retirada da importe, fecha y estado. */
    static TransferSummary toTransfer(CheckingAccount source, CheckingAccount target, Transaction withdrawal) {
        return TransferSummary.builder()
                .sourceAccountId(source.id())
                .sourceAccountNumber(source.accountNumber().value())
                .targetAccountId(target.id())
                .targetAccountNumber(target.accountNumber().value())
                .amount(withdrawal.amount().amount())
                .currency(withdrawal.amount().currency().getCurrencyCode())
                .occurredOn(withdrawal.occurredOn())
                .status(withdrawal.status().name())
                .build();
    }
}

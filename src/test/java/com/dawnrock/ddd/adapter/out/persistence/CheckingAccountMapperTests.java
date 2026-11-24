package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CustomerSegment;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.support.TestAccounts;

class CheckingAccountMapperTests {

    private static final AccountNumber OTHER = new AccountNumber("ES8099990001450000000001");

    /** Alto patrimonio con 500: apertura, retirada rechazada, transferencia enviada y revisión de segmento pendiente. */
    private static CheckingAccount accountWithHistory() {
        CheckingAccount account = TestAccounts.anAccount().segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("500.00").open();
        account.deposit(MonetaryAmount.euros("25.00"));
        account.tryWithdraw(MonetaryAmount.euros("5000.00"));
        account.tryWithdrawForTransfer(MonetaryAmount.euros("100.00"), OTHER);
        account.pullDomainEvents();
        return account;
    }

    @Test
    @DisplayName("Una cuenta pasa a su fila y vuelve sin cambiar ningún dato")
    void anAccountGoesToItsRowAndBackWithoutChangingAnyData() {
        CheckingAccount original = accountWithHistory();

        CheckingAccount leida = CheckingAccountMapper.toDomain(
                CheckingAccountMapper.copyInto(original, new CheckingAccountEntity(original.id())));

        assertThat(leida.id()).isEqualTo(original.id());
        assertThat(leida.accountNumber()).isEqualTo(original.accountNumber());
        assertThat(leida.primaryHolder()).isEqualTo(original.primaryHolder());
        assertThat(leida.primaryHolder().fullName()).isEqualTo(original.primaryHolder().fullName());
        assertThat(leida.primaryHolder().taxId()).isEqualTo(original.primaryHolder().taxId());
        assertThat(leida.primaryHolder().segment()).isEqualTo(CustomerSegment.HIGH_NET_WORTH);
        assertThat(leida.currentBalance()).isEqualTo(MonetaryAmount.euros("425.00"));
        assertThat(leida.nextSegmentReview()).isEqualTo(original.nextSegmentReview()).isPresent();
        assertThat(leida.transactions()).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(original.transactions());
        assertThat(leida.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Al guardar de nuevo una cuenta solo se añaden a su fila los movimientos nuevos")
    void savingAgainOnlyAddsTheNewTransactions() {
        CheckingAccount account = accountWithHistory();
        CheckingAccountEntity fila = CheckingAccountMapper.copyInto(account, new CheckingAccountEntity(account.id()));
        TransactionEntity primera = fila.transactions.getFirst();

        account.deposit(MonetaryAmount.euros("10.00"));
        CheckingAccountMapper.copyInto(account, fila);

        assertThat(fila.transactions).hasSize(5);
        assertThat(fila.transactions.getFirst()).isSameAs(primera);
        assertThat(fila.transactions.getLast().position).isEqualTo(4);
        assertThat(fila.transactions.getLast().occurredOn).isBeforeOrEqualTo(Instant.now());
        assertThat(fila.balance).isEqualByComparingTo("435.00");
    }
}

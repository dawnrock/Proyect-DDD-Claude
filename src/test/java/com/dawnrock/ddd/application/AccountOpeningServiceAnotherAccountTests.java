package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Abrir otra cuenta un cliente que ya tiene una: el titular sale de sus datos de cliente. */
class AccountOpeningServiceAnotherAccountTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final Caller GRACE = Caller.builder().username("grace").admin(false).build();

    private CheckingAccountRepository accounts;
    private CheckingAccountQueryService service;
    private AccountOpeningCommands opening;
    private AccountSummary first;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().accounts(accounts).build();
        service = fixture.queryService();
        opening = fixture.accountOpeningService();
        first = opening.signUp(SignUpCommand.builder().fullName("Ada Lovelace").taxId("12345678Z")
                .email("ada@example.com").username("ada").initialDeposit(new BigDecimal("1000")).build());
    }

    private static OpenAnotherAccountCommand deposit(String amount) {
        return OpenAnotherAccountCommand.builder().initialDeposit(new BigDecimal(amount)).build();
    }

    @Test
    @DisplayName("El cliente acaba con dos cuentas de IBAN distintos y de monA Bank, y las dos se listan")
    void theCustomerEndsUpWithTwoAccountsWithDifferentIbans() {
        AccountSummary second = opening.openAnotherAccount(ADA, deposit("200.00"));

        assertThat(second.accountNumber()).isNotEqualTo(first.accountNumber());
        assertThat(new AccountNumber(second.accountNumber()).isMonABank()).isTrue();
        assertThat(second.balance()).isEqualByComparingTo("200.00");
        assertThat(service.listAccounts(ADA)).extracting(AccountSummary::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    @DisplayName("El titular de la nueva cuenta copia el nombre y el documento del cliente")
    void theHolderCopiesTheCustomerNameAndDocument() {
        AccountSummary second = opening.openAnotherAccount(ADA, deposit("200.00"));

        assertThat(second.holderName()).isEqualTo("Ada Lovelace");
        assertThat(accounts.findById(second.id()).orElseThrow().primaryHolder().taxId())
                .isEqualTo(new TaxId("12345678Z"));
    }

    @Test
    @DisplayName("Quien no es cliente no puede abrir otra cuenta, y no se crea nada")
    void aNonCustomerCannotOpenAnotherAccount() {
        assertThatExceptionOfType(NotACustomerException.class)
                .isThrownBy(() -> opening.openAnotherAccount(GRACE, deposit("200.00")))
                .withMessage("El usuario grace no es cliente de monA Bank: no puede abrir otra cuenta");
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un administrador no puede abrir otra cuenta aunque su usuario coincida con el de un cliente")
    void anAdministratorCannotOpenAnotherAccount() {
        Caller adminNamedAda = Caller.builder().username("ada").admin(true).build();

        assertThatExceptionOfType(NotACustomerException.class)
                .isThrownBy(() -> opening.openAnotherAccount(ADMIN, deposit("200.00")));
        assertThatExceptionOfType(NotACustomerException.class)
                .isThrownBy(() -> opening.openAnotherAccount(adminNamedAda, deposit("200.00")));
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un depósito inferior a 50 EUR se rechaza y no se abre la cuenta (R1)")
    void anInsufficientDepositIsRejected() {
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> opening.openAnotherAccount(ADA, deposit("10.00")));
        assertThat(service.listAccounts(ADA)).hasSize(1);
    }

    @Test
    @DisplayName("Un depósito en otra divisa se rechaza y no se abre la cuenta")
    void aDepositInAnotherCurrencyIsRejected() {
        assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> opening.openAnotherAccount(ADA,
                OpenAnotherAccountCommand.builder().initialDeposit(new BigDecimal("200")).currency("USD").build()));
        assertThat(service.listAccounts(ADA)).hasSize(1);
    }
}

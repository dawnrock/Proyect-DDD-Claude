package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.support.TestAccounts;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** El administrador sustituye el IBAN de una cuenta por otro que genera el banco (R32). */
class AccountOpeningServiceRegenerateAccountNumberTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();

    private CheckingAccountRepository accounts;
    private CheckingAccountQueryService service;
    private AccountOpeningCommands opening;
    private AccountSummary account;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().accounts(accounts).build();
        service = fixture.queryService();
        opening = fixture.accountOpeningService();
        account = opening.signUp(SignUpCommand.builder().fullName("Ada Lovelace").taxId("12345678Z")
                .email("ada@example.com").username("ada").initialDeposit(new BigDecimal("1000")).build());
    }

    @Test
    @DisplayName("El administrador recibe el resumen con un IBAN nuevo de Mon-A Bank y la cuenta lo conserva")
    void theAdministratorGetsTheSummaryWithANewIban() {
        AccountSummary regenerated = opening.regenerateAccountNumber(ADMIN, account.id());

        assertThat(regenerated.id()).isEqualTo(account.id());
        assertThat(regenerated.accountNumber()).isNotEqualTo(account.accountNumber());
        assertThat(new AccountNumber(regenerated.accountNumber()).isMonABank()).isTrue();
        assertThat(service.findAccount(ADMIN, account.id()).accountNumber()).isEqualTo(regenerated.accountNumber());
    }

    @Test
    @DisplayName("El IBAN antiguo deja de resolverse y el nuevo se resuelve")
    void theOldIbanNoLongerResolvesAndTheNewOneDoes() {
        AccountSummary regenerated = opening.regenerateAccountNumber(ADMIN, account.id());

        assertThat(accounts.findByAccountNumber(new AccountNumber(account.accountNumber()))).isEmpty();
        assertThat(accounts.findByAccountNumber(new AccountNumber(regenerated.accountNumber())))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(account.id()));
    }

    @Test
    @DisplayName("Una cuenta con un IBAN de otro banco pasa a tener uno de Mon-A Bank")
    void anAccountWithAnotherBanksIbanGetsAMonABankOne() {
        UUID id = accounts.save(TestAccounts.anAccount().initialDeposit("500.00").open()).id();

        AccountSummary regenerated = opening.regenerateAccountNumber(ADMIN, id);

        assertThat(new AccountNumber(regenerated.accountNumber()).isMonABank()).isTrue();
    }

    @Test
    @DisplayName("Un cliente no puede regenerar el IBAN y la cuenta no cambia")
    void aCustomerCannotRegenerateTheIban() {
        assertThatExceptionOfType(ForbiddenOperationException.class)
                .isThrownBy(() -> opening.regenerateAccountNumber(ADA, account.id()));

        assertThat(service.findAccount(ADA, account.id()).accountNumber()).isEqualTo(account.accountNumber());
    }

    @Test
    @DisplayName("Un id desconocido da cuenta no encontrada")
    void anUnknownIdGivesAccountNotFound() {
        UUID unknown = UUID.randomUUID();

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> opening.regenerateAccountNumber(ADMIN, unknown));
    }

    @Test
    @DisplayName("Si el número generado ya lo usa otra cuenta, se rechaza y la cuenta conserva el suyo")
    void whenTheGeneratedNumberIsAlreadyUsedTheAccountKeepsItsOwn() {
        UUID id = accounts.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open()).id();
        AccountNumber used = new AccountNumber("ES9121000418450200051332");
        AccountOpeningService faulty = ApplicationServicesFixture.builder()
                .accounts(accounts)
                .factory(new CheckingAccountFactory(() -> new AccountNumber(account.accountNumber()), TaxAgencyTestAdapters.incomeVerifier()))
                .build()
                .accountOpeningService();

        assertThatExceptionOfType(com.dawnrock.ddd.domain.model.DuplicateAccountNumberException.class)
                .isThrownBy(() -> faulty.regenerateAccountNumber(ADMIN, id));
        assertThat(accounts.findById(id).orElseThrow().accountNumber()).isEqualTo(used);
    }
}

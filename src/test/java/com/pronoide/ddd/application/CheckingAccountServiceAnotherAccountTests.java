package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.domain.service.MoneyTransferService;
import com.pronoide.ddd.domain.service.SegmentReviewService;

/** Abrir otra cuenta un cliente que ya tiene una: el titular sale de sus datos de cliente. */
class CheckingAccountServiceAnotherAccountTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final Caller GRACE = Caller.builder().username("grace").admin(false).build();

    private CheckingAccountRepository accounts;
    private CheckingAccountService service;
    private AccountSummary first;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        service = new CheckingAccountService(accounts, PersistenceTestAdapters.ownership(),
                mock(DomainEventPublisher.class),
                new CheckingAccountFactory(PersistenceTestAdapters.accountNumbers(),
                        TaxAgencyTestAdapters.incomeVerifier()),
                new MoneyTransferService(), new SegmentReviewService(TaxAgencyTestAdapters.incomeVerifier()),
                Clock.systemUTC(), PersistenceTestAdapters.unitOfWork(), PersistenceTestAdapters.customers(),
                PersistenceTestAdapters.customerOwnership());
        first = service.signUp(SignUpCommand.builder().fullName("Ada Lovelace").taxId("12345678Z")
                .email("ada@example.com").username("ada").initialDeposit(new BigDecimal("1000")).build());
    }

    private static OpenAnotherAccountCommand deposit(String amount) {
        return OpenAnotherAccountCommand.builder().initialDeposit(new BigDecimal(amount)).build();
    }

    @Test
    @DisplayName("El cliente acaba con dos cuentas de IBAN distintos y de Mon-A Bank, y las dos se listan")
    void el_cliente_acaba_con_dos_cuentas_con_iban_distintos() {
        AccountSummary second = service.openAnotherAccount(ADA, deposit("200.00"));

        assertThat(second.accountNumber()).isNotEqualTo(first.accountNumber());
        assertThat(new AccountNumber(second.accountNumber()).isMonABank()).isTrue();
        assertThat(second.balance()).isEqualByComparingTo("200.00");
        assertThat(service.listAccounts(ADA)).extracting(AccountSummary::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    @DisplayName("El titular de la nueva cuenta copia el nombre y el documento del cliente")
    void el_titular_copia_el_nombre_y_el_documento_del_cliente() {
        AccountSummary second = service.openAnotherAccount(ADA, deposit("200.00"));

        assertThat(second.holderName()).isEqualTo("Ada Lovelace");
        assertThat(accounts.findById(second.id()).orElseThrow().primaryHolder().taxId())
                .isEqualTo(new TaxId("12345678Z"));
    }

    @Test
    @DisplayName("Quien no es cliente no puede abrir otra cuenta, y no se crea nada")
    void quien_no_es_cliente_no_puede_abrir_otra_cuenta() {
        assertThatExceptionOfType(NotACustomerException.class)
                .isThrownBy(() -> service.openAnotherAccount(GRACE, deposit("200.00")))
                .withMessageContaining("grace");
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un administrador no puede abrir otra cuenta aunque su usuario coincida con el de un cliente")
    void un_administrador_no_puede_abrir_otra_cuenta() {
        Caller adminNamedAda = Caller.builder().username("ada").admin(true).build();

        assertThatExceptionOfType(NotACustomerException.class)
                .isThrownBy(() -> service.openAnotherAccount(ADMIN, deposit("200.00")));
        assertThatExceptionOfType(NotACustomerException.class)
                .isThrownBy(() -> service.openAnotherAccount(adminNamedAda, deposit("200.00")));
        assertThat(accounts.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Un depósito inferior a 50 EUR se rechaza y no se abre la cuenta (R1)")
    void un_deposito_insuficiente_se_rechaza() {
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> service.openAnotherAccount(ADA, deposit("10.00")));
        assertThat(service.listAccounts(ADA)).hasSize(1);
    }

    @Test
    @DisplayName("Un depósito en otra divisa se rechaza y no se abre la cuenta")
    void un_deposito_en_otra_divisa_se_rechaza() {
        assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> service.openAnotherAccount(ADA,
                OpenAnotherAccountCommand.builder().initialDeposit(new BigDecimal("200")).currency("USD").build()));
        assertThat(service.listAccounts(ADA)).hasSize(1);
    }
}

package com.dawnrock.ddd.application;

import static com.dawnrock.ddd.application.ServiceTestSupport.ADMIN;
import static com.dawnrock.ddd.application.ServiceTestSupport.publishedBatches;
import static com.dawnrock.ddd.application.ServiceTestSupport.retailOpening;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CheckingAccountOpened;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Apertura de cuentas con la factoría y los adaptadores en memoria detrás de los puertos (#174). */
@DisplayName("Abrir cuenta con la factoría")
class AccountOpeningServiceTests {

    private CheckingAccountRepository repository;
    private DomainEventPublisher publisher;
    private CheckingAccountQueries queries;
    private AccountOpeningCommands opening;

    @BeforeEach
    void setUp() {
        repository = spy(PersistenceTestAdapters.accounts());
        publisher = mock(DomainEventPublisher.class);
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder()
                .accounts(repository)
                .publisher(publisher)
                .build();
        queries = fixture.queryService();
        opening = fixture.accountOpeningService();
    }

    private OpenAccountCommand.Builder byTaxId(String taxId) {
        return OpenAccountCommand.builder()
                .holderName("Grace Hopper")
                .holderTaxId(taxId)
                .holderEmail("grace@example.com")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("grace");
    }

    @Test
    @DisplayName("Con NIF la cuenta recibe un IBAN generado y el resumen lo devuelve")
    void withATaxIdTheAccountGetsAGeneratedIban() {
        AccountSummary summary = opening.openAccount(ADMIN, byTaxId("87654321X").build());

        assertThat(summary.accountNumber()).startsWith("ES").hasSize(24);
        assertThat(repository.findByAccountNumber(new AccountNumber(summary.accountNumber()))).isPresent();
    }

    @Test
    @DisplayName("Con ingresos verificados por encima del umbral el resumen es de alto patrimonio")
    void withHighIncomeTheSummaryIsHighNetWorth() {
        AccountSummary summary = opening.openAccount(ADMIN, byTaxId("87654321X").build());

        assertThat(summary.segment()).isEqualTo("HIGH_NET_WORTH");
    }

    @Test
    @DisplayName("Con ingresos bajos o un NIF desconocido el resumen es minorista")
    void withLowIncomeOrAnUnknownTaxIdTheSummaryIsRetail() {
        assertThat(opening.openAccount(ADMIN, byTaxId("12345678Z").build()).segment()).isEqualTo("RETAIL");
        assertThat(opening.openAccount(ADMIN, byTaxId("99999999R").holderName("Alan Turing")
                .holderEmail("alan@example.com").ownerUsername("alan").build()).segment()).isEqualTo("RETAIL");
    }

    @Test
    @DisplayName("Un número generado que ya está en uso se rechaza y no se guarda")
    void aGeneratedNumberAlreadyInUseIsRejected() {
        String used = opening.openAccount(ADMIN, retailOpening().build()).accountNumber();
        AccountOpeningService faulty = ApplicationServicesFixture.builder()
                .accounts(repository)
                .publisher(publisher)
                .factory(new CheckingAccountFactory(() -> new AccountNumber(used), TaxAgencyTestAdapters.incomeVerifier()))
                .build()
                .accountOpeningService();

        assertThatExceptionOfType(DuplicateAccountNumberException.class)
                .isThrownBy(() -> faulty.openAccount(ADMIN, byTaxId("87654321X").build()))
                .withMessageContaining(used);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Con NIF se publican los eventos de apertura y se asigna el propietario")
    void withATaxIdTheEventsArePublishedAndTheOwnerIsAssigned() {
        clearInvocations(publisher);

        AccountSummary summary = opening.openAccount(ADMIN, byTaxId("87654321X").build());

        assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                .hasAtLeastOneElementOfType(CheckingAccountOpened.class));
        assertThat(queries.listAccounts(Caller.builder().username("grace").admin(false).build()))
                .extracting(AccountSummary::id).containsExactly(summary.id());
    }

    @Test
    @DisplayName("Un depósito válido abre la cuenta, la guarda y devuelve su resumen")
    void aValidDepositOpensSavesAndReturnsTheAccountSummary() {
        AccountSummary summary = opening.openAccount(ADMIN, retailOpening().build());

        assertThat(summary.holderName()).isEqualTo("Ada Lovelace");
        assertThat(summary.balance()).isEqualByComparingTo("100.00");
        assertThat(summary.balance().scale()).isEqualTo(2);
        assertThat(summary.currency()).isEqualTo("EUR");
        assertThat(repository.findById(summary.id())).isPresent();
    }

    @Test
    @DisplayName("Un depósito en otra divisa se rechaza y no se guarda")
    void aDepositInAnotherCurrencyIsRejectedAndNotSaved() {
        OpenAccountCommand inDollars = retailOpening().currency("USD").build();

        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> opening.openAccount(ADMIN, inDollars));
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Un depósito por debajo del mínimo se rechaza también con NIF")
    void aLowDepositIsRejectedAlsoWithATaxId() {
        OpenAccountCommand tooLow = byTaxId("87654321X").initialDeposit(new BigDecimal("49.99")).build();

        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> opening.openAccount(ADMIN, tooLow));
        assertThat(repository.findAll()).isEmpty();
    }
}

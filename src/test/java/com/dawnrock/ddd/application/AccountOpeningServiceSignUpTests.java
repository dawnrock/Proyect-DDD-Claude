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
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Alta de un cliente nuevo con su primera cuenta (R28-R30). Adaptadores en memoria reales. */
class AccountOpeningServiceSignUpTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private CheckingAccountRepository accounts;
    private AccountOwnershipRepository owners;
    private CustomerRepository customers;
    private CustomerOwnershipRepository customerOwners;
    private CheckingAccountQueryService service;
    private AccountOpeningCommands opening;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        owners = PersistenceTestAdapters.ownership();
        customers = PersistenceTestAdapters.customers();
        customerOwners = PersistenceTestAdapters.customerOwnership();
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder()
                .accounts(accounts)
                .ownership(owners)
                .customers(customers)
                .customerOwnership(customerOwners)
                .build();
        service = fixture.queryService();
        opening = fixture.accountOpeningService();
    }

    private static SignUpCommand.Builder signUp() {
        return SignUpCommand.builder()
                .fullName("Margaret Hamilton")
                .taxId("00000000T")
                .email("Margaret@Example.com")
                .username("margaret")
                .initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("El alta registra al cliente y abre su cuenta con un IBAN de Mon-A Bank a nombre de su usuario")
    void signUpRegistersTheCustomerAndOpensTheirAccountWithAMonABankIban() {
        AccountSummary summary = opening.signUp(signUp().build());

        assertThat(new AccountNumber(summary.accountNumber()).isMonABank()).isTrue();
        assertThat(summary.holderName()).isEqualTo("Margaret Hamilton");
        assertThat(summary.balance()).isEqualByComparingTo("100.00");
        assertThat(owners.ownerOf(summary.id())).contains("margaret");
        assertThat(customerOwners.customerOf("margaret")).contains(new TaxId("00000000T"));
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).hasValueSatisfying(customer ->
                assertThat(customer.email().value()).isEqualTo("margaret@example.com"));
    }

    @Test
    @DisplayName("El titular de la cuenta lleva el documento del cliente y el cliente ve su cuenta")
    void theAccountHolderCarriesTheCustomerDocument() {
        AccountSummary summary = opening.signUp(signUp().taxId(" 00000000 t ").build());

        assertThat(accounts.findById(summary.id()).orElseThrow().primaryHolder().taxId())
                .isEqualTo(new TaxId("00000000T"));
        assertThat(service.listAccounts(Caller.builder().username("margaret").admin(false).build()))
                .containsExactly(summary);
    }

    @Test
    @DisplayName("El segmento sale de los ingresos verificados por la agencia: 87654321X es de alto patrimonio")
    void theSegmentComesFromTheIncomeVerifiedByTheTaxAgency() {
        AccountSummary grace = opening.signUp(signUp().fullName("Grace Hopper").taxId("87654321X")
                .username("grace").build());
        AccountSummary margaret = opening.signUp(signUp().build());

        assertThat(service.findAccount(ADMIN, grace.id()).segment()).isEqualTo("HIGH_NET_WORTH");
        assertThat(service.findAccount(ADMIN, margaret.id()).segment()).isEqualTo("RETAIL");
    }

    @Test
    @DisplayName("Un documento que ya es de un cliente se rechaza aunque cambie el correo, y no se abre nada (R30)")
    void aDocumentAlreadyBelongingToACustomerIsRejectedEvenWithAnotherEmail() {
        opening.signUp(signUp().build());

        assertThatExceptionOfType(DuplicateCustomerException.class)
                .isThrownBy(() -> opening.signUp(signUp().email("otro@example.com").username("otra").build()))
                .withMessageContaining("00000000T");
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(customerOwners.customerOf("otra")).isEmpty();
    }

    @Test
    @DisplayName("Un usuario que ya es de otro cliente se rechaza y no se abre nada (R30)")
    void anotherCustomersUsernameIsRejectedAndNothingIsOpened() {
        opening.signUp(signUp().build());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> opening.signUp(signUp().taxId("12345678Z").build()))
                .withMessage("El usuario margaret ya es de otro cliente");
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(customers.findByTaxId(new TaxId("12345678Z"))).isEmpty();
    }

    @Test
    @DisplayName("Un usuario que ya es propietario de una cuenta se rechaza y no se abre nada (R30)")
    void aUsernameThatOwnsAnAccountIsRejectedAndNothingIsOpened() {
        owners.assign(java.util.UUID.randomUUID(), "margaret");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> opening.signUp(signUp().build()))
                .withMessage("El usuario margaret ya es de otro cliente");
        assertThat(accounts.findAll()).isEmpty();
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
    }

    @Test
    @DisplayName("Un depósito inferior a 50 EUR se rechaza y no deja ni cliente ni cuenta (R1, R30)")
    void anInsufficientDepositLeavesNeitherCustomerNorAccount() {
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> opening.signUp(signUp().initialDeposit(new BigDecimal("49.99")).build()));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
        assertThat(customerOwners.customerOf("margaret")).isEmpty();
    }

    @Test
    @DisplayName("Tras un alta fallida el mismo documento puede darse de alta con un depósito válido")
    void afterAFailedSignUpTheSameDocumentCanSignUp() {
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> opening.signUp(signUp().initialDeposit(new BigDecimal("10")).build()));

        assertThat(opening.signUp(signUp().build()).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Un depósito en otra divisa se rechaza y no deja nada guardado")
    void aDepositInAnotherCurrencyIsRejectedAndNothingIsSaved() {
        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> opening.signUp(signUp().currency("USD").build()));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
    }

    @Test
    @DisplayName("Un documento con la letra de control incorrecta se rechaza y no deja nada guardado (R28)")
    void aDocumentWithTheWrongLetterIsRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> opening.signUp(signUp().taxId("00000000A").build()))
                .withMessage("La letra de control del documento 00000000A no es correcta");

        assertThat(accounts.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Dos altas reciben IBAN distintos")
    void twoSignUpsGetDifferentIbans() {
        AccountSummary first = opening.signUp(signUp().build());
        AccountSummary second = opening.signUp(signUp().taxId("12345678Z").username("ada")
                .email("ada@example.com").build());

        assertThat(first.accountNumber()).isNotEqualTo(second.accountNumber());
    }

    @Test
    @DisplayName("Un usuario es de un cliente si tiene cliente asignado o ya es propietario de una cuenta")
    void aUsernameIsTakenWhenItHasACustomerOrAnAccount() {
        opening.signUp(signUp().build());
        owners.assign(java.util.UUID.randomUUID(), "demo");

        assertThat(service.isUsernameTaken("margaret")).isTrue();
        assertThat(service.isUsernameTaken("demo")).isTrue();
        assertThat(service.isUsernameTaken("libre")).isFalse();
    }
}

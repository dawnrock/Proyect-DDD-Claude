package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.Customer;
import com.dawnrock.ddd.domain.model.CustomerRepository;
import com.dawnrock.ddd.domain.model.EmailAddress;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/**
 * Alta de cuenta del administrador para un cliente nuevo o existente, identificado por su documento (contrato
 * {@code docs/contracts/customer-sign-up.md}). Adaptadores en memoria reales.
 */
class CheckingAccountServiceAdminOpeningTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();

    private CheckingAccountRepository accounts;
    private CustomerRepository customers;
    private CustomerOwnershipRepository customerOwners;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        customers = PersistenceTestAdapters.customers();
        customerOwners = PersistenceTestAdapters.customerOwnership();
        service = ApplicationServicesFixture.builder()
                .accounts(accounts)
                .customers(customers)
                .customerOwnership(customerOwners)
                .build()
                .service();
    }

    private static OpenAccountCommand.Builder command() {
        return OpenAccountCommand.builder()
                .holderName("Ada Lovelace")
                .holderTaxId("12345678Z")
                .holderEmail("ada@example.com")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("ada");
    }

    private static OpenAccountCommand.Builder other() {
        return OpenAccountCommand.builder()
                .holderName("Margaret Hamilton")
                .holderTaxId("00000000T")
                .holderEmail("margaret@example.com")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("margaret");
    }

    @Test
    @DisplayName("Un documento nuevo registra al cliente con su correo y su usuario, y el detalle los muestra")
    void aNewDocumentRegistersTheCustomer() {
        AccountSummary summary = service.openAccount(ADMIN, command().build());

        assertThat(customers.findByTaxId(new TaxId("12345678Z"))).hasValueSatisfying(customer ->
                assertThat(customer.email().value()).isEqualTo("ada@example.com"));
        assertThat(customerOwners.usernameOf(new TaxId("12345678Z"))).contains("ada");
        AccountDetails details = service.findAccount(ADMIN, summary.id());
        assertThat(details.holderTaxId()).isEqualTo("12345678Z");
        assertThat(details.holderEmail()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("A un cliente existente se le abre otra cuenta indicando solo su documento")
    void anExistingCustomerGetsAnotherAccountWithOnlyTheirDocument() {
        AccountSummary first = service.openAccount(ADMIN, command().build());

        AccountSummary second = service.openAccount(ADMIN, OpenAccountCommand.builder()
                .holderTaxId(" 12345678 z ").initialDeposit(new BigDecimal("75")).build());

        assertThat(second.holderName()).isEqualTo("Ada Lovelace");
        assertThat(second.accountNumber()).isNotEqualTo(first.accountNumber());
        assertThat(service.listAccounts(ADA)).hasSize(2);
    }

    @Test
    @DisplayName("A un cliente existente se le abre otra cuenta repitiendo sus mismos datos")
    void anExistingCustomerGetsAnotherAccountRepeatingTheirData() {
        service.openAccount(ADMIN, command().build());

        service.openAccount(ADMIN, command().holderEmail(" ADA@example.com ").build());

        assertThat(service.listAccounts(ADA)).hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"nombre, Ada Byron, ada@example.com, ada",
            "correo, Ada Lovelace, ada@otro.example, ada",
            "usuario, Ada Lovelace, ada@example.com, grace"})
    @DisplayName("Unos datos distintos de los del cliente existente se rechazan y no se abre nada")
    void dataDifferentFromTheExistingCustomerIsRejected(String field, String name, String email,
                                                                         String username) {
        service.openAccount(ADMIN, command().build());
        OpenAccountCommand contradicting = command()
                .holderName(name).holderEmail(email).ownerUsername(username).build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.openAccount(ADMIN, contradicting))
                .withMessageContaining("ya es de un cliente");
        assertThat(accounts.findAll()).hasSize(1);
    }

    @ParameterizedTest(name = "sin {0}")
    @CsvSource({"nombre, '', ada@example.com, ada, El nombre del titular es obligatorio para un cliente nuevo",
            "correo, Ada Lovelace, '', ada, El correo del titular es obligatorio para un cliente nuevo",
            "usuario, Ada Lovelace, ada@example.com, '', El usuario del titular es obligatorio para un cliente nuevo"})
    @DisplayName("A un cliente nuevo le faltan datos: se rechaza sin registrar ni abrir nada")
    void aNewCustomerWithMissingDataIsRejected(String field, String name, String email, String username,
                                            String message) {
        OpenAccountCommand incomplete = command()
                .holderName(name).holderEmail(email).ownerUsername(username).build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.openAccount(ADMIN, incomplete))
                .withMessage(message);
        assertThat(accounts.findAll()).isEmpty();
        assertThat(customers.findByTaxId(new TaxId("12345678Z"))).isEmpty();
    }

    @Test
    @DisplayName("El usuario de otro cliente no se asigna a un cliente nuevo y no se abre nada")
    void anotherCustomersUsernameIsNotAssignedToANewCustomer() {
        service.openAccount(ADMIN, command().build());
        OpenAccountCommand stealing = other().ownerUsername("ada").build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.openAccount(ADMIN, stealing))
                .withMessage("El usuario ada ya es de otro cliente");
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
        assertThat(customerOwners.customerOf("ada")).contains(new TaxId("12345678Z"));
    }

    @Test
    @DisplayName("El alta pública no puede usar el usuario que el administrador dio a un cliente")
    void publicSignUpCannotUseAnotherCustomersUsername() {
        service.openAccount(ADMIN, command().build());
        SignUpCommand signUp = SignUpCommand.builder().fullName("Margaret Hamilton").taxId("00000000T")
                .email("margaret@example.com").username("ada").initialDeposit(new BigDecimal("100")).build();

        assertThat(service.isUsernameTaken("ada")).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> service.signUp(signUp));
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
    }

    @Test
    @DisplayName("Un cliente existente sin usuario asignado da un error de petición y no un error interno")
    void anExistingCustomerWithoutUsernameGivesARequestError() {
        customers.save(Customer.register("Ada Lovelace", new TaxId("12345678Z"),
                new EmailAddress("ada@example.com")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.openAccount(ADMIN, OpenAccountCommand.builder()
                        .holderTaxId("12345678Z").initialDeposit(new BigDecimal("75")).build()))
                .withMessageContaining("no tiene usuario");
        assertThat(accounts.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Un depósito insuficiente no deja registrado al cliente nuevo")
    void anInsufficientDepositDoesNotRegisterTheNewCustomer() {
        OpenAccountCommand tooLow = command().initialDeposit(new BigDecimal("10")).build();

        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> service.openAccount(ADMIN, tooLow));
        assertThat(customers.findByTaxId(new TaxId("12345678Z"))).isEmpty();
        assertThat(customerOwners.customerOf("ada")).isEmpty();
    }

    @Test
    @DisplayName("Un depósito en otra divisa no deja registrado al cliente nuevo")
    void aDepositInAnotherCurrencyDoesNotRegisterTheNewCustomer() {
        OpenAccountCommand inDollars = command().currency("USD").build();

        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> service.openAccount(ADMIN, inDollars));
        assertThat(customers.findByTaxId(new TaxId("12345678Z"))).isEmpty();
    }

    @Test
    @DisplayName("Un cliente abierto por el administrador puede abrir otra cuenta él mismo")
    void aCustomerOpenedByTheAdministratorCanOpenAnotherAccount() {
        service.openAccount(ADMIN, command().build());

        AccountSummary another = service.openAnotherAccount(ADA,
                OpenAnotherAccountCommand.builder().initialDeposit(new BigDecimal("60")).build());

        assertThat(another.holderName()).isEqualTo("Ada Lovelace");
        assertThat(service.listAccounts(ADA)).hasSize(2);
    }
}

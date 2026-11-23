package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.integration.TaxAgencyClient;
import com.dawnrock.ddd.persistence.InMemoryAccountNumberSequence;
import com.dawnrock.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.dawnrock.ddd.persistence.InMemoryCheckingAccountRepository;
import com.dawnrock.ddd.persistence.InMemoryCustomerRepository;

/** Un cliente con sesión abre otra cuenta indicando solo el depósito. Repositorios en memoria reales. */
class CheckingAccountServiceAnotherAccountTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final Caller GRACE = Caller.builder().username("grace").admin(false).build();

    private InMemoryCheckingAccountRepository accounts;
    private CheckingAccountService service;
    private AccountSummary first;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryCheckingAccountRepository();
        service = new CheckingAccountService(accounts, new InMemoryAccountOwnershipRepository(),
                new InMemoryCustomerRepository(), new InMemoryAccountNumberSequence(), new TaxAgencyClient(),
                Clock.systemUTC());
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
}

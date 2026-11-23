package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.DuplicateCustomerException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.integration.TaxAgencyClient;
import com.dawnrock.ddd.persistence.InMemoryAccountNumberSequence;
import com.dawnrock.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.dawnrock.ddd.persistence.InMemoryCheckingAccountRepository;
import com.dawnrock.ddd.persistence.InMemoryCustomerRepository;

/** Alta de un cliente nuevo con su primera cuenta (R27-R31). Repositorios en memoria reales. */
class CheckingAccountServiceSignUpTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private InMemoryCheckingAccountRepository accounts;
    private InMemoryAccountOwnershipRepository owners;
    private InMemoryCustomerRepository customers;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryCheckingAccountRepository();
        owners = new InMemoryAccountOwnershipRepository();
        customers = new InMemoryCustomerRepository();
        service = new CheckingAccountService(accounts, owners, customers, new InMemoryAccountNumberSequence(),
                new TaxAgencyClient(), Clock.systemUTC());
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
    void el_alta_registra_al_cliente_y_abre_su_cuenta_con_un_iban_de_mon_a_bank() {
        AccountSummary summary = service.signUp(signUp().build());

        assertThat(new AccountNumber(summary.accountNumber()).isMonABank()).isTrue();
        assertThat(summary.holderName()).isEqualTo("Margaret Hamilton");
        assertThat(summary.balance()).isEqualByComparingTo("100.00");
        assertThat(owners.ownerOf(summary.id())).contains("margaret");
        assertThat(customers.findByUsername("margaret")).hasValueSatisfying(customer -> {
            assertThat(customer.taxId()).isEqualTo(new TaxId("00000000T"));
            assertThat(customer.email().value()).isEqualTo("margaret@example.com");
        });
    }

    @Test
    @DisplayName("El titular de la cuenta lleva el documento del cliente y el cliente ve su cuenta")
    void el_titular_de_la_cuenta_lleva_el_documento_del_cliente() {
        AccountSummary summary = service.signUp(signUp().taxId(" 00000000 t ").build());

        assertThat(accounts.findById(summary.id()).orElseThrow().primaryHolder().taxId())
                .isEqualTo(new TaxId("00000000T"));
        assertThat(service.listAccounts(Caller.builder().username("margaret").admin(false).build()))
                .containsExactly(summary);
    }

    @Test
    @DisplayName("El segmento sale de los ingresos verificados por la agencia: 87654321X es de alto patrimonio")
    void el_segmento_sale_de_los_ingresos_verificados_por_la_agencia() {
        AccountSummary grace = service.signUp(signUp().fullName("Grace Hopper").taxId("87654321X")
                .username("grace").build());
        AccountSummary margaret = service.signUp(signUp().build());

        assertThat(service.findAccount(ADMIN, grace.id()).segment()).isEqualTo("HIGH_NET_WORTH");
        assertThat(service.findAccount(ADMIN, margaret.id()).segment()).isEqualTo("RETAIL");
    }

    @Test
    @DisplayName("Un documento que ya es de un cliente se rechaza aunque cambie el correo, y no se abre nada (R29)")
    void un_documento_que_ya_es_de_un_cliente_se_rechaza_aunque_cambie_el_correo() {
        service.signUp(signUp().build());

        assertThatExceptionOfType(DuplicateCustomerException.class)
                .isThrownBy(() -> service.signUp(signUp().email("otro@example.com").username("otra").build()))
                .withMessageContaining("00000000T");
        assertThat(accounts.findAll()).hasSize(1);
        assertThat(customers.findByUsername("otra")).isEmpty();
    }

    @Test
    @DisplayName("Un depósito inferior a 50 EUR se rechaza y no deja ni cliente ni cuenta (R1, R29)")
    void un_deposito_insuficiente_no_deja_ni_cliente_ni_cuenta() {
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> service.signUp(signUp().initialDeposit(new BigDecimal("49.99")).build()));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
        assertThat(customers.findByUsername("margaret")).isEmpty();
    }

    @Test
    @DisplayName("Tras un alta fallida el mismo documento puede darse de alta con un depósito válido")
    void tras_un_alta_fallida_el_mismo_documento_puede_darse_de_alta() {
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> service.signUp(signUp().initialDeposit(new BigDecimal("10")).build()));

        assertThat(service.signUp(signUp().build()).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Un depósito en otra divisa se rechaza y no deja nada guardado")
    void un_deposito_en_otra_divisa_se_rechaza_y_no_deja_nada_guardado() {
        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> service.signUp(signUp().currency("USD").build()));

        assertThat(accounts.findAll()).isEmpty();
        assertThat(customers.findByTaxId(new TaxId("00000000T"))).isEmpty();
    }

    @Test
    @DisplayName("Un documento con la letra de control incorrecta se rechaza y no deja nada guardado (R27)")
    void un_documento_con_la_letra_incorrecta_se_rechaza() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.signUp(signUp().taxId("00000000A").build()))
                .withMessage("La letra de control del documento 00000000A no es correcta");

        assertThat(accounts.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Dos altas reciben IBAN distintos")
    void dos_altas_reciben_iban_distintos() {
        AccountSummary first = service.signUp(signUp().build());
        AccountSummary second = service.signUp(signUp().taxId("12345678Z").username("ada")
                .email("ada@example.com").build());

        assertThat(first.accountNumber()).isNotEqualTo(second.accountNumber());
    }
}

package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

/**
 * Reglas de propiedad de las cuentas (#50): un administrador accede a todo; un usuario solo a sus cuentas, y una ajena
 * se trata como inexistente. Repositorios en memoria reales.
 */
class CheckingAccountServiceOwnershipTests {

    private static final String ADA_IBAN = "ES9121000418450200051332";
    private static final String GRACE_IBAN = "ES7921000813610123456789";

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final Caller GRACE = Caller.builder().username("grace").admin(false).build();

    private InMemoryCheckingAccountRepository accounts;
    private InMemoryAccountOwnershipRepository owners;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryCheckingAccountRepository();
        owners = new InMemoryAccountOwnershipRepository();
        service = new CheckingAccountService(accounts, owners);
    }

    private static OpenAccountCommand.Builder command(String accountNumber, String owner) {
        return OpenAccountCommand.builder()
                .accountNumber(accountNumber)
                .holderName("Titular")
                .segment("RETAIL")
                .initialDeposit(new BigDecimal("500"))
                .ownerUsername(owner);
    }

    @Nested
    @DisplayName("Abrir cuenta con propietario")
    class OpenAccount {

        @Test
        @DisplayName("Un administrador abre la cuenta y queda registrada a nombre del usuario propietario")
        void un_administrador_abre_la_cuenta_y_queda_registrada_a_nombre_del_propietario() {
            AccountSummary summary = service.openAccount(ADMIN, command(ADA_IBAN, "ada").build());

            assertThat(summary.accountNumber()).isEqualTo(ADA_IBAN);
            assertThat(owners.ownerOf(summary.id())).contains("ada");
        }

        @Test
        @DisplayName("Un usuario que no es administrador no puede abrir cuentas y no se guarda nada")
        void un_usuario_que_no_es_administrador_no_puede_abrir_cuentas() {
            OpenAccountCommand open = command(ADA_IBAN, "ada").build();

            assertThatExceptionOfType(ForbiddenOperationException.class)
                    .isThrownBy(() -> service.openAccount(ADA, open));
            assertThat(accounts.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Abrir una cuenta sin usuario propietario lanza IllegalArgumentException y no se guarda nada")
        void abrir_una_cuenta_sin_usuario_propietario_lanza_illegal_argument_exception() {
            OpenAccountCommand withoutOwner = command(ADA_IBAN, "").build();

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, withoutOwner))
                    .withMessageContaining("propietario");
            assertThat(accounts.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un nombre de usuario inválido lo rechaza el command al construirse")
        void un_nombre_de_usuario_invalido_lo_rechaza_el_command() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> command(ADA_IBAN, "Ada Lovelace").build());
        }

        @Test
        @DisplayName("La vía antigua sin Caller registra el propietario cuando el command lo trae")
        void la_via_antigua_registra_el_propietario_cuando_el_command_lo_trae() {
            AccountSummary summary = service.openAccount(command(ADA_IBAN, "ada").build());

            assertThat(owners.ownerOf(summary.id())).contains("ada");
        }

        @Test
        @DisplayName("La vía antigua sin propietario abre la cuenta sin propietario")
        void la_via_antigua_sin_propietario_abre_la_cuenta_sin_propietario() {
            AccountSummary summary = service.openAccount(command(ADA_IBAN, "").build());

            assertThat(owners.ownerOf(summary.id())).isEmpty();
        }
    }
}

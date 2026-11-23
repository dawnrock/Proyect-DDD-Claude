package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.integration.TaxAgencyClient;
import com.dawnrock.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.dawnrock.ddd.persistence.InMemoryCheckingAccountRepository;
import com.dawnrock.ddd.support.MutableClock;

class SegmentReviewJobTests {

    private static final Caller ADMIN = CallerDef.system();

    @Test
    @DisplayName("La tarea diaria devuelve a minorista a quien lleva un mes bajo el patrimonio mínimo")
    void la_tarea_diaria_devuelve_a_minorista_a_quien_lleva_un_mes_bajo_el_minimo() {
        MutableClock clock = new MutableClock();
        CheckingAccountService service = new CheckingAccountService(
                new InMemoryCheckingAccountRepository(), new InMemoryAccountOwnershipRepository(),
                new com.dawnrock.ddd.persistence.InMemoryCustomerRepository(),
                new com.dawnrock.ddd.persistence.InMemoryAccountNumberSequence(),
                new TaxAgencyClient(), clock);
        UUID id = service.openAccount(ADMIN, OpenAccountCommand.builder()
                .holderName("Ada Lovelace").holderTaxId("12345678Z").holderEmail("ada@example.com")
                .initialDeposit(new BigDecimal("100000")).ownerUsername("ada").build()).id();
        service.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("500")).build());
        clock.advance(Duration.ofDays(32));

        new SegmentReviewJob(service).reviewSegments();

        assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("RETAIL");
    }
}

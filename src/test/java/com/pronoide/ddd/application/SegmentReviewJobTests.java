package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;
import com.pronoide.ddd.support.MutableClock;

class SegmentReviewJobTests {

    private static final Caller ADMIN = CallerDef.system();

    @Test
    @DisplayName("La tarea diaria devuelve a minorista a quien lleva un mes bajo el patrimonio mínimo")
    void la_tarea_diaria_devuelve_a_minorista_a_quien_lleva_un_mes_bajo_el_minimo() {
        MutableClock clock = new MutableClock();
        CheckingAccountService service = new CheckingAccountService(
                new InMemoryCheckingAccountRepository(), new InMemoryAccountOwnershipRepository(), clock);
        UUID id = service.openAccount(ADMIN, OpenAccountCommand.builder()
                .accountNumber("ES9121000418450200051332").holderName("Grace Hopper").holderTaxId("87654321X")
                .initialDeposit(new BigDecimal("100000")).ownerUsername("grace").build()).id();
        service.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("500")).build());
        clock.advance(Duration.ofDays(32));

        new SegmentReviewJob(service).reviewSegments();

        assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("RETAIL");
    }
}

package com.pronoide.ddd.application.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.WithdrawalRejected;

@ExtendWith(OutputCaptureExtension.class)
class AuditLogListenerTests {

    private final AuditLogListener listener = new AuditLogListener();

    @Test
    @DisplayName("Anota en la auditoría cada evento de dominio con su tipo, su cuenta y su instante")
    void anota_cada_evento_con_su_tipo_su_cuenta_y_su_instante(CapturedOutput output) {
        UUID accountId = UUID.randomUUID();
        Instant occurredOn = Instant.parse("2026-11-03T10:15:30Z");

        listener.on(new WithdrawalRejected(accountId, UUID.randomUUID(), MonetaryAmount.euros("600.00"), occurredOn));

        assertThat(output).contains("Auditoría", "WithdrawalRejected", accountId.toString(), "2026-11-03T10:15:30Z");
    }
}

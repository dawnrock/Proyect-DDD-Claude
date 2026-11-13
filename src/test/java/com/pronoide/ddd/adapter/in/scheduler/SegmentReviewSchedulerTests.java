package com.pronoide.ddd.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.pronoide.ddd.application.CheckingAccountCommands;

@ExtendWith(OutputCaptureExtension.class)
class SegmentReviewSchedulerTests {

    @Test
    @DisplayName("La tarea programada pide la revisión de segmentos por el puerto de entrada y anota el resultado")
    void la_tarea_programada_pide_la_revision_por_el_puerto_de_entrada(CapturedOutput output) {
        CheckingAccountCommands commands = mock(CheckingAccountCommands.class);
        given(commands.reviewSegments()).willReturn(2);

        new SegmentReviewScheduler(commands).reviewSegments();

        verify(commands).reviewSegments();
        assertThat(output).contains("Revisión de segmentos", "2 titulares vuelven a minorista");
    }
}

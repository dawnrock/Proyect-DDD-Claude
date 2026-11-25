package com.dawnrock.ddd.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.dawnrock.ddd.application.SegmentReviewCommands;

@ExtendWith(OutputCaptureExtension.class)
class SegmentReviewSchedulerTests {

    @Test
    @DisplayName("La tarea programada pide la revisión de segmentos por el puerto de entrada y anota el resultado")
    void theScheduledTaskRequestsTheReviewThroughTheInputPort(CapturedOutput output) {
        SegmentReviewCommands commands = mock(SegmentReviewCommands.class);
        given(commands.reviewSegments()).willReturn(2);

        new SegmentReviewScheduler(commands).reviewSegments();

        verify(commands).reviewSegments();
        assertThat(output).contains("Revisión de segmentos", "2 titulares vuelven a minorista");
    }
}

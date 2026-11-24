package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SegmentLabelsTests {

    @Test
    @DisplayName("Cada segmento tiene su nombre en español")
    void eachSegmentHasItsSpanishName() {
        assertThat(SegmentLabels.of("RETAIL")).isEqualTo("Minorista");
        assertThat(SegmentLabels.of("HIGH_NET_WORTH")).isEqualTo("Alto patrimonio");
    }

    @Test
    @DisplayName("Un segmento desconocido se muestra tal cual")
    void anUnknownSegmentIsShownAsIs() {
        assertThat(SegmentLabels.of("PRIVATE_BANKING")).isEqualTo("PRIVATE_BANKING");
    }
}

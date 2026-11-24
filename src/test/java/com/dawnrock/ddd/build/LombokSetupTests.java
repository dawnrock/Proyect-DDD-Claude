package com.dawnrock.ddd.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

class LombokSetupTests {

    @Getter
    @RequiredArgsConstructor
    static class Sample {
        private final String name;
    }

    @Test
    @DisplayName("Lombok genera accesores fluidos al estilo record")
    void lombokGeneratesRecordStyleFluentAccessors() {
        Sample sample = new Sample("Ada Lovelace");

        assertThat(sample.name()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("Lombok no genera accesores con el prefijo get")
    void lombokDoesNotGenerateGetPrefixedAccessors() {
        assertThatExceptionOfType(NoSuchMethodException.class)
                .isThrownBy(() -> Sample.class.getDeclaredMethod("getName"));
    }
}

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
    @DisplayName("Lombok genera getters JavaBean, los que enlaza Spring MVC en los formularios web")
    void lombokGeneratesJavaBeanGetters() {
        Sample sample = new Sample("Ada Lovelace");

        assertThat(sample.getName()).isEqualTo("Ada Lovelace");
    }

    @Test
    @DisplayName("Lombok no genera accesores fluidos al estilo record (ADR 0026)")
    void lombokDoesNotGenerateFluentAccessors() {
        assertThatExceptionOfType(NoSuchMethodException.class)
                .isThrownBy(() -> Sample.class.getDeclaredMethod("name"));
    }
}

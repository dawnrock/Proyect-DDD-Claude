package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Normalización del usuario escrito por una persona (#152, #157). */
class UsernamesTests {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Elber|elber",
            "'  ADA  '|ada",
            "'\tgrace.h\n'|grace.h",
            "alan_t-2|alan_t-2"})
    @DisplayName("El usuario se normaliza sin espacios alrededor y en minúsculas")
    void theUsernameIsNormalisedTrimmedAndLowerCase(String typed, String expected) {
        assertThat(Usernames.normalize(typed)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Un usuario nulo sigue siendo nulo")
    void aNullUsernameStaysNull() {
        assertThat(Usernames.normalize(null)).isNull();
    }
}

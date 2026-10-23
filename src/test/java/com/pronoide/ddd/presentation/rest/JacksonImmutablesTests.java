package com.pronoide.ddd.presentation.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.immutables.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.application.ApplicationValue;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

/** Riesgo del ADR 0003: ¿sirve Immutables para los DTOs REST con Jackson 3? */
class JacksonImmutablesTests {

    @ApplicationValue
    @Value.Immutable
    @JsonSerialize(as = SampleDto.class)
    @JsonDeserialize(as = SampleDto.class)
    interface SampleDtoDef {
        String holderName();

        BigDecimal amount();
    }

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("Un DTO de Immutables se serializa y deserializa con Jackson 3")
    void un_dto_de_immutables_se_serializa_y_deserializa_con_jackson_3() {
        SampleDto dto = SampleDto.builder().holderName("Ada Lovelace").amount(new BigDecimal("50.00")).build();

        String json = mapper.writeValueAsString(dto);
        SampleDto leido = mapper.readValue(json, SampleDto.class);

        assertThat(json).contains("\"holderName\":\"Ada Lovelace\"").contains("\"amount\":50.00");
        assertThat(leido).isEqualTo(dto);
    }
}

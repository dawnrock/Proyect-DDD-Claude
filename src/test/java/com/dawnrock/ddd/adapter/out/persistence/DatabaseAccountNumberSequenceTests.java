package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.AccountNumberSequence;
import com.dawnrock.ddd.domain.model.AccountNumberSequenceContract;

/** Lo propio de la numeración sobre la secuencia de la base; lo común está en {@link AccountNumberSequenceContract}. */
class DatabaseAccountNumberSequenceTests extends AccountNumberSequenceContract {

    @Override
    protected AccountNumberSequence createSequence() {
        return new DatabaseAccountNumberSequence(JpaTestDatabase.bean(JdbcTemplate.class));
    }

    @Test
    @DisplayName("Una numeración nueva sobre la misma base sigue donde lo dejó la anterior, como tras un reinicio")
    void una_numeracion_nueva_sobre_la_misma_base_sigue_donde_lo_dejo_la_anterior() {
        AccountNumber last = sequence.next();

        AccountNumber afterRestart = createSequence().next();

        assertThat(afterRestart).isNotEqualTo(last);
        assertThat(Long.parseLong(afterRestart.value().substring(14)))
                .isEqualTo(Long.parseLong(last.value().substring(14)) + 1);
    }
}

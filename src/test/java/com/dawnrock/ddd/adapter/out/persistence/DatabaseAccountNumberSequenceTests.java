package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
    void aNewSequenceOnTheSameDatabaseContinuesWhereThePreviousOneStopped() {
        AccountNumber last = sequence.next();

        AccountNumber afterRestart = createSequence().next();

        assertThat(afterRestart).isNotEqualTo(last);
        assertThat(Long.parseLong(afterRestart.value().substring(14)))
                .isEqualTo(Long.parseLong(last.value().substring(14)) + 1);
    }

    @Test
    @DisplayName("Si la secuencia no devuelve ningún valor, falla con un error claro en lugar de un NullPointerException")
    void aSequenceReturningNothingFailsWithAClearError() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        given(jdbc.queryForObject(anyString(), eq(Long.class))).willReturn(null);

        assertThatIllegalStateException()
                .isThrownBy(() -> new DatabaseAccountNumberSequence(jdbc).next())
                .withMessageContaining("account_number_seq");
    }
}

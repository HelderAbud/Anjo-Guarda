package com.anjoguarda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class UniqueConstraintHandlerTest {

    private final UniqueConstraintHandler handler = new UniqueConstraintHandler();

    @Test
    void uniqueViolationRespondsWithConflict() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("duplicado", new SQLException("duplicado", "23505"));

        var response = handler.conflict(error);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).isEqualTo("Registro já existe");
    }

    @Test
    void otherIntegrityErrorsStayUnhandled() {
        DataIntegrityViolationException error = new DataIntegrityViolationException("chave", new SQLException("chave", "23503"));

        assertThatThrownBy(() -> handler.conflict(error)).isSameAs(error);
    }
}

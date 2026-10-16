package com.anjoguarda.api;

import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UniqueConstraintHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException error) {
        if (!uniqueViolation(error)) {
            throw error;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Registro já existe");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static boolean uniqueViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

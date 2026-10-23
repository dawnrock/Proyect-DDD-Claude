package com.pronoide.ddd.presentation.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.pronoide.ddd.domain.model.DomainException;

/**
 * Único punto de traducción de errores a HTTP para la API REST, con el formato {@link ProblemDetail}
 * (RFC 9457). Ver "Traducción de errores" en {@code docs/arquitectura/fase-1-capas.md}.
 */
@RestControllerAdvice(annotations = RestController.class)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    static final String BUSINESS_RULE_TITLE = "Regla de negocio incumplida";

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleBusinessRule(DomainException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, BUSINESS_RULE_TITLE, exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}

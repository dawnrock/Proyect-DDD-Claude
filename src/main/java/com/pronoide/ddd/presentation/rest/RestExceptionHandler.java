package com.pronoide.ddd.presentation.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.pronoide.ddd.application.NotFoundException;
import com.pronoide.ddd.domain.model.DomainException;

/**
 * Único punto de traducción de errores a HTTP para la API REST, con el formato {@link ProblemDetail}
 * (RFC 9457). Ver "Traducción de errores" en {@code docs/arquitectura/fase-1-capas.md}:
 * reglas de negocio → 422, recurso inexistente → 404, argumentos inválidos y validación → 400.
 */
@RestControllerAdvice(annotations = RestController.class)
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    static final String BUSINESS_RULE_TITLE = "Regla de negocio incumplida";
    static final String NOT_FOUND_TITLE = "Recurso inexistente";
    static final String BAD_REQUEST_TITLE = "Petición no válida";

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleBusinessRule(DomainException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, BUSINESS_RULE_TITLE, exception.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_TITLE, exception.getMessage());
    }

    /** Argumentos que el dominio rechaza (importe no positivo, IBAN mal formado): no son reglas de negocio. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE,
                "La petición tiene %d campo(s) no válido(s)".formatted(errors.size()));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}

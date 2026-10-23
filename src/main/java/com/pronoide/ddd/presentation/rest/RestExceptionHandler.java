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

import lombok.extern.slf4j.Slf4j;

/**
 * Único punto de traducción de errores a HTTP para la API REST, con el formato {@link ProblemDetail}
 * (RFC 9457). Ver "Traducción de errores" en {@code docs/arquitectura/fase-1-capas.md}:
 * reglas de negocio → 422, recurso inexistente → 404, argumentos inválidos y validación → 400.
 */
@Slf4j
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

    /**
     * Argumentos que el dominio rechaza (importe no positivo, IBAN mal formado): no son reglas de negocio.
     * Es una red de seguridad: la presentación debería validarlos antes. Se registra en el log porque una
     * {@code IllegalArgumentException} también puede venir de un error de programación.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        log.warn("Argumento no válido rechazado con 400", exception);
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TITLE, exception.getMessage());
    }

    /** Los 400 que resuelve Spring (JSON mal formado, parámetros ausentes o de tipo incorrecto) usan el título del contrato. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && status.value() == HttpStatus.BAD_REQUEST.value()) {
            problem.setTitle(BAD_REQUEST_TITLE);
        }
        return response;
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

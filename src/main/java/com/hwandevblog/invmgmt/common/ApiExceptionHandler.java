package com.hwandevblog.invmgmt.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({DuplicateResourceException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "A resource with the same business key already exists");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String detailMessage) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, detailMessage);
        detail.setTitle(status.getReasonPhrase());
        return detail;
    }
}

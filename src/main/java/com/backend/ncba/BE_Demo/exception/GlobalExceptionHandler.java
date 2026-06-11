package com.backend.ncba.BE_Demo.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(build(HttpStatus.NOT_FOUND, ex.getReason(), req.getRequestURI(), null));
    }

    @ExceptionHandler(SoapServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSoapUnavailable(
            SoapServiceUnavailableException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(build(HttpStatus.SERVICE_UNAVAILABLE, ex.getReason(), req.getRequestURI(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> {
                    String path = cv.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return new ErrorResponse.FieldError(field, cv.getMessage());
                })
                .toList();
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, "Constraint violation", req.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest()
                .body(build(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequestURI(), null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(status)
                .body(build(status, ex.getReason() != null ? ex.getReason() : ex.getMessage(),
                        req.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest req) {
        return ResponseEntity.internalServerError()
                .body(build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                        req.getRequestURI(), null));
    }

    private ErrorResponse build(HttpStatus status, String message, String path,
                                List<ErrorResponse.FieldError> fieldErrors) {
        return new ErrorResponse(status.value(), status.name(), message, path, fieldErrors, Instant.now());
    }
}

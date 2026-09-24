package com.connectly.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Maps exceptions to the standard error shape. Also bound to HTTP status codes
 * so Spring Security / Servlet errors render consistently.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, org.springframework.web.context.request.WebRequest req) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiErrorResponse(Instant.now(), ex.getStatus().value(), ex.getCode(),
                        ex.getMessage(), uri(req)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
            org.springframework.web.context.request.WebRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(java.util.stream.Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(Instant.now(), 400, "VALIDATION_ERROR", message, uri(req)));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            org.springframework.web.context.request.WebRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(Instant.now(), 403, "FORBIDDEN",
                        "You do not have permission to perform this action", uri(req)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex,
            org.springframework.web.context.request.WebRequest req) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled exception on {}", uri(req), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(Instant.now(), 500, "INTERNAL_ERROR",
                        "An unexpected error occurred", uri(req)));
    }

    private static String uri(org.springframework.web.context.request.WebRequest req) {
        String desc = req.getDescription(false);
        return desc.startsWith("uri=") ? desc.substring(4) : desc;
    }
}

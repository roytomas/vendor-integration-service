package com.vendor.integration.exception;

import com.vendor.integration.web.dto.ErrorResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

/**
 * Centralized, reactive error handling for the vendor integration API.
 * Maps internal/resilience exceptions onto appropriate HTTP statuses so
 * callers of this service get a clear, consistent error contract instead
 * of a generic 500.
 */
@RestControllerAdvice
@Order(-2)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VendorApiException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleVendorApiException(VendorApiException ex, ServerWebExchange exchange) {
        log.warn("Vendor API exception: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), exchange);
    }

    @ExceptionHandler(VendorNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleVendorNotFound(VendorNotFoundException ex, ServerWebExchange exchange) {
        log.warn("Vendor not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), exchange);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCircuitOpen(CallNotPermittedException ex, ServerWebExchange exchange) {
        log.warn("Circuit breaker open: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Vendor system is currently unavailable, please retry later", exchange);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRateLimited(RequestNotPermitted ex, ServerWebExchange exchange) {
        log.warn("Rate limiter rejected call: {}", ex.getMessage());
        return build(HttpStatus.TOO_MANY_REQUESTS, "Too many requests to the vendor system, please slow down", exchange);
    }

    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTimeout(TimeoutException ex, ServerWebExchange exchange) {
        log.warn("Vendor call timed out: {}", ex.getMessage());
        return build(HttpStatus.GATEWAY_TIMEOUT, "Vendor system did not respond in time", exchange);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneric(Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", exchange);
    }

    private Mono<ResponseEntity<ErrorResponse>> build(HttpStatus status, String message, ServerWebExchange exchange) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value()
        );
        return Mono.just(ResponseEntity.status(status).body(body));
    }
}

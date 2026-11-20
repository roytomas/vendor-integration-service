package com.vendor.integration.web.dto;

import java.time.Instant;

/**
 * Standard error body returned by {@link com.vendor.integration.exception.GlobalExceptionHandler}.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}

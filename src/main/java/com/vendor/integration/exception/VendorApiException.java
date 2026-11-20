package com.vendor.integration.exception;

/**
 * Raised when the vendor integration layer cannot obtain a valid response
 * from the external vendor system (non-2xx response, malformed payload,
 * or after resilience fallbacks have been exhausted).
 */
public class VendorApiException extends RuntimeException {

    public VendorApiException(String message) {
        super(message);
    }

    public VendorApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

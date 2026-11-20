package com.vendor.integration.exception;

/**
 * Raised when a vendor is requested by its {@code vendorId} but no such
 * vendor is currently persisted.
 */
public class VendorNotFoundException extends RuntimeException {

    public VendorNotFoundException(String vendorId) {
        super("No vendor found with vendorId " + vendorId);
    }
}

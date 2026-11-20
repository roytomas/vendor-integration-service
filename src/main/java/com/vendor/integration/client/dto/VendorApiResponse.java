package com.vendor.integration.client.dto;

/**
 * Raw payload shape as returned by the external vendor system. Field names
 * intentionally mirror a typical third-party vendor contract (different
 * from our own domain model), so an explicit mapping step is required.
 */
public record VendorApiResponse(
        String vendorCode,
        String vendorName,
        String contactEmail,
        String contactPhone,
        String addressLine,
        String vendorStatus,
        String lastUpdated
) {
}

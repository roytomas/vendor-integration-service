package com.vendor.integration.web.dto;

import com.vendor.integration.domain.VendorStatus;

import java.time.Instant;

/**
 * Vendor representation exposed by our own REST API (as opposed to the raw
 * {@link com.vendor.integration.client.dto.VendorApiResponse} shape used by
 * the external vendor system).
 */
public record VendorResponseDTO(
        Long id,
        String vendorId,
        String name,
        String email,
        String phone,
        String address,
        VendorStatus status,
        Instant updatedAt
) {
}

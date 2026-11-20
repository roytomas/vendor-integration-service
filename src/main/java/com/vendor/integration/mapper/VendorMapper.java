package com.vendor.integration.mapper;

import com.vendor.integration.client.dto.VendorApiResponse;
import com.vendor.integration.domain.Vendor;
import com.vendor.integration.domain.VendorStatus;
import com.vendor.integration.web.dto.VendorResponseDTO;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;

/**
 * Translates between the vendor system's wire format, our persisted domain
 * model and the DTO we expose on our own API. Keeping this mapping in one
 * place isolates the rest of the application from the vendor's field names
 * and value conventions.
 */
@Component
public class VendorMapper {

    /**
     * Applies an incoming vendor payload onto an existing (or newly created)
     * {@link Vendor} entity, preserving the persistence identity/version.
     */
    public Vendor applyToEntity(VendorApiResponse source, Vendor target) {
        target.setVendorId(source.vendorCode());
        target.setName(source.vendorName());
        target.setEmail(source.contactEmail());
        target.setPhone(source.contactPhone());
        target.setAddress(source.addressLine());
        target.setStatus(toVendorStatus(source.vendorStatus()));
        target.setVendorUpdatedAt(parseInstant(source.lastUpdated()));
        return target;
    }

    public VendorResponseDTO toResponseDTO(Vendor vendor) {
        return new VendorResponseDTO(
                vendor.getId(),
                vendor.getVendorId(),
                vendor.getName(),
                vendor.getEmail(),
                vendor.getPhone(),
                vendor.getAddress(),
                vendor.getStatus(),
                vendor.getUpdatedAt()
        );
    }

    private VendorStatus toVendorStatus(String rawStatus) {
        if (rawStatus == null) {
            return VendorStatus.UNKNOWN;
        }
        return switch (rawStatus.trim().toUpperCase()) {
            case "ACTIVE", "A", "ENABLED" -> VendorStatus.ACTIVE;
            case "INACTIVE", "I", "DISABLED" -> VendorStatus.INACTIVE;
            default -> VendorStatus.UNKNOWN;
        };
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException e) {
            return null;
        }
    }
}

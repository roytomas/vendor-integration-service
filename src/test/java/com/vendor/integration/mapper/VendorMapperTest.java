package com.vendor.integration.mapper;

import com.vendor.integration.client.dto.VendorApiResponse;
import com.vendor.integration.domain.Vendor;
import com.vendor.integration.domain.VendorStatus;
import com.vendor.integration.web.dto.VendorResponseDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VendorMapperTest {

    private final VendorMapper mapper = new VendorMapper();

    @Test
    void appliesVendorPayloadOntoNewEntity() {
        VendorApiResponse source = new VendorApiResponse(
                "VEND-100", "Test Vendor", "test@vendor.com", "+1-000-000-0000",
                "1 Test St", "ACTIVE", "2025-01-01T00:00:00Z");

        Vendor target = new Vendor();
        Vendor result = mapper.applyToEntity(source, target);

        assertThat(result.getVendorId()).isEqualTo("VEND-100");
        assertThat(result.getName()).isEqualTo("Test Vendor");
        assertThat(result.getEmail()).isEqualTo("test@vendor.com");
        assertThat(result.getPhone()).isEqualTo("+1-000-000-0000");
        assertThat(result.getAddress()).isEqualTo("1 Test St");
        assertThat(result.getStatus()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(result.getVendorUpdatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    @Test
    void normalizesKnownStatusAliases() {
        assertThat(mapper.applyToEntity(responseWithStatus("A"), new Vendor()).getStatus())
                .isEqualTo(VendorStatus.ACTIVE);
        assertThat(mapper.applyToEntity(responseWithStatus("DISABLED"), new Vendor()).getStatus())
                .isEqualTo(VendorStatus.INACTIVE);
        assertThat(mapper.applyToEntity(responseWithStatus("whatever"), new Vendor()).getStatus())
                .isEqualTo(VendorStatus.UNKNOWN);
        assertThat(mapper.applyToEntity(responseWithStatus(null), new Vendor()).getStatus())
                .isEqualTo(VendorStatus.UNKNOWN);
    }

    @Test
    void tolerantOfUnparsableTimestamp() {
        Vendor result = mapper.applyToEntity(responseWithLastUpdated("not-a-date"), new Vendor());
        assertThat(result.getVendorUpdatedAt()).isNull();
    }

    @Test
    void mapsEntityToResponseDTO() {
        Vendor vendor = new Vendor();
        vendor.setId(1L);
        vendor.setVendorId("VEND-100");
        vendor.setName("Test Vendor");
        vendor.setEmail("test@vendor.com");
        vendor.setPhone("+1-000-000-0000");
        vendor.setAddress("1 Test St");
        vendor.setStatus(VendorStatus.ACTIVE);
        vendor.setUpdatedAt(Instant.parse("2025-01-01T00:00:00Z"));

        VendorResponseDTO dto = mapper.toResponseDTO(vendor);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.vendorId()).isEqualTo("VEND-100");
        assertThat(dto.status()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(dto.updatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
    }

    private VendorApiResponse responseWithStatus(String status) {
        return new VendorApiResponse("VEND-100", "Test Vendor", "test@vendor.com",
                "+1-000-000-0000", "1 Test St", status, "2025-01-01T00:00:00Z");
    }

    private VendorApiResponse responseWithLastUpdated(String lastUpdated) {
        return new VendorApiResponse("VEND-100", "Test Vendor", "test@vendor.com",
                "+1-000-000-0000", "1 Test St", "ACTIVE", lastUpdated);
    }
}

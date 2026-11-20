package com.vendor.integration.service;

import com.vendor.integration.client.VendorApiClient;
import com.vendor.integration.client.dto.VendorApiResponse;
import com.vendor.integration.domain.Vendor;
import com.vendor.integration.domain.VendorStatus;
import com.vendor.integration.mapper.VendorMapper;
import com.vendor.integration.repository.VendorRepository;
import com.vendor.integration.web.dto.SyncResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorIntegrationServiceTest {

    @Mock
    private VendorApiClient vendorApiClient;

    @Mock
    private VendorRepository vendorRepository;

    private VendorIntegrationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new VendorIntegrationService(vendorApiClient, vendorRepository, new VendorMapper());
    }

    @Test
    void syncVendorsInsertsNewVendor() {
        VendorApiResponse apiResponse = new VendorApiResponse(
                "VEND-001", "Acme", "a@acme.com", "+1-000", "Addr", "ACTIVE", "2025-01-01T00:00:00Z");

        when(vendorApiClient.fetchVendors()).thenReturn(Flux.just(apiResponse));
        when(vendorRepository.findByVendorId("VEND-001")).thenReturn(Mono.empty());
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.syncVendors())
                .expectNextMatches(result -> result.vendorsProcessed() == 1 && "COMPLETED".equals(result.status()))
                .verifyComplete();

        verify(vendorRepository).save(any(Vendor.class));
    }

    @Test
    void syncVendorsUpdatesExistingVendor() {
        VendorApiResponse apiResponse = new VendorApiResponse(
                "VEND-001", "Acme Updated", "a@acme.com", "+1-000", "Addr", "ACTIVE", "2025-02-01T00:00:00Z");

        Vendor existing = new Vendor();
        existing.setId(42L);
        existing.setVendorId("VEND-001");
        existing.setName("Acme");
        existing.setStatus(VendorStatus.ACTIVE);

        when(vendorApiClient.fetchVendors()).thenReturn(Flux.just(apiResponse));
        when(vendorRepository.findByVendorId("VEND-001")).thenReturn(Mono.just(existing));
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.syncVendors())
                .expectNextMatches(result -> result.vendorsProcessed() == 1)
                .verifyComplete();

        verify(vendorRepository).save(org.mockito.ArgumentMatchers.argThat(
                v -> v.getId().equals(42L) && v.getName().equals("Acme Updated")));
    }

    @Test
    void syncVendorsSkipsFailedPersistenceButContinues() {
        VendorApiResponse ok = new VendorApiResponse(
                "VEND-001", "Acme", "a@acme.com", "+1-000", "Addr", "ACTIVE", "2025-01-01T00:00:00Z");
        VendorApiResponse failing = new VendorApiResponse(
                "VEND-002", "Bad Vendor", "b@bad.com", "+1-111", "Addr2", "ACTIVE", "2025-01-01T00:00:00Z");

        when(vendorApiClient.fetchVendors()).thenReturn(Flux.just(ok, failing));
        when(vendorRepository.findByVendorId("VEND-001")).thenReturn(Mono.empty());
        when(vendorRepository.findByVendorId("VEND-002")).thenReturn(Mono.empty());
        when(vendorRepository.save(any(Vendor.class))).thenAnswer(invocation -> {
            Vendor v = invocation.getArgument(0);
            if ("VEND-002".equals(v.getVendorId())) {
                return Mono.error(new RuntimeException("db failure"));
            }
            return Mono.just(v);
        });

        StepVerifier.create(service.syncVendors())
                .expectNextMatches(result -> result.vendorsProcessed() == 1)
                .verifyComplete();
    }

    @Test
    void getAllVendorsMapsPersistedEntities() {
        Vendor vendor = new Vendor();
        vendor.setId(1L);
        vendor.setVendorId("VEND-001");
        vendor.setName("Acme");
        vendor.setStatus(VendorStatus.ACTIVE);

        when(vendorRepository.findAll()).thenReturn(Flux.just(vendor));

        StepVerifier.create(service.getAllVendors())
                .expectNextMatches(dto -> dto.vendorId().equals("VEND-001") && dto.name().equals("Acme"))
                .verifyComplete();
    }
}

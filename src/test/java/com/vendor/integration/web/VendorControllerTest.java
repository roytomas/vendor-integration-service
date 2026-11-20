package com.vendor.integration.web;

import com.vendor.integration.domain.VendorStatus;
import com.vendor.integration.service.VendorIntegrationService;
import com.vendor.integration.web.dto.SyncResultDTO;
import com.vendor.integration.web.dto.VendorResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = VendorController.class)
class VendorControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private VendorIntegrationService vendorIntegrationService;

    @Test
    void getAllVendorsReturnsPersistedVendors() {
        VendorResponseDTO dto = new VendorResponseDTO(
                1L, "VEND-001", "Acme", "a@acme.com", "+1-000", "Addr",
                VendorStatus.ACTIVE, Instant.parse("2025-01-01T00:00:00Z"));

        when(vendorIntegrationService.getAllVendors()).thenReturn(Flux.just(dto));

        webTestClient.get().uri("/api/vendors")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(VendorResponseDTO.class)
                .hasSize(1)
                .contains(dto);
    }

    @Test
    void syncTriggersIntegrationServiceAndReturnsResult() {
        when(vendorIntegrationService.syncVendors())
                .thenReturn(Mono.just(new SyncResultDTO(3, "COMPLETED")));

        webTestClient.post().uri("/api/vendors/sync")
                .exchange()
                .expectStatus().isOk()
                .expectBody(SyncResultDTO.class)
                .isEqualTo(new SyncResultDTO(3, "COMPLETED"));
    }
}

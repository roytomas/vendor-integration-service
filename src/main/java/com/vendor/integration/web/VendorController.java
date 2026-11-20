package com.vendor.integration.web;

import com.vendor.integration.service.VendorIntegrationService;
import com.vendor.integration.web.dto.SyncResultDTO;
import com.vendor.integration.web.dto.VendorResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive REST API exposing vendor data to consumers of this service.
 */
@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorIntegrationService vendorIntegrationService;

    public VendorController(VendorIntegrationService vendorIntegrationService) {
        this.vendorIntegrationService = vendorIntegrationService;
    }

    /**
     * Returns all vendor details currently persisted in the database,
     * streamed reactively as newline-delimited JSON.
     */
    @GetMapping
    public Flux<VendorResponseDTO> getAllVendors() {
        return vendorIntegrationService.getAllVendors();
    }

    /**
     * Triggers an on-demand synchronization against the external vendor
     * system, in addition to the background scheduled sync.
     */
    @PostMapping("/sync")
    public Mono<SyncResultDTO> syncVendors() {
        return vendorIntegrationService.syncVendors();
    }
}

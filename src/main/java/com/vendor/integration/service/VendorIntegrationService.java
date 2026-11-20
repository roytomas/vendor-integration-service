package com.vendor.integration.service;

import com.vendor.integration.client.VendorApiClient;
import com.vendor.integration.client.dto.VendorApiResponse;
import com.vendor.integration.domain.Vendor;
import com.vendor.integration.exception.VendorNotFoundException;
import com.vendor.integration.mapper.VendorMapper;
import com.vendor.integration.repository.VendorRepository;
import com.vendor.integration.web.dto.SyncResultDTO;
import com.vendor.integration.web.dto.VendorResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Core integration/orchestration service: pulls vendor records through the
 * resilient {@link VendorApiClient}, maps them into the domain model and
 * upserts them via the reactive {@link VendorRepository}. Also serves
 * already-persisted vendor data to the rest of the application.
 */
@Service
public class VendorIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(VendorIntegrationService.class);

    private final VendorApiClient vendorApiClient;
    private final VendorRepository vendorRepository;
    private final VendorMapper vendorMapper;

    public VendorIntegrationService(VendorApiClient vendorApiClient,
                                     VendorRepository vendorRepository,
                                     VendorMapper vendorMapper) {
        this.vendorApiClient = vendorApiClient;
        this.vendorRepository = vendorRepository;
        this.vendorMapper = vendorMapper;
    }

    /**
     * Fetches the current vendor list from the external system and upserts
     * each record into the local database. Failures from the vendor API are
     * already handled by {@link VendorApiClient}'s resilience fallbacks, so
     * this stream only has to deal with persistence-level issues.
     */
    public Mono<SyncResultDTO> syncVendors() {
        return vendorApiClient.fetchVendors()
                .flatMap(this::upsert, 4)
                .count()
                .map(count -> new SyncResultDTO(count, "COMPLETED"))
                .doOnNext(result -> log.info("Vendor sync completed, {} vendor(s) processed", result.vendorsProcessed()));
    }

    private Mono<Vendor> upsert(VendorApiResponse apiResponse) {
        return vendorRepository.findByVendorId(apiResponse.vendorCode())
                .defaultIfEmpty(newVendor())
                .map(existing -> vendorMapper.applyToEntity(apiResponse, existing))
                .map(this::touchTimestamps)
                .flatMap(vendorRepository::save)
                .doOnError(e -> log.error("Failed to persist vendor {}: {}", apiResponse.vendorCode(), e.toString()))
                .onErrorResume(e -> Mono.empty());
    }

    private Vendor newVendor() {
        Vendor vendor = new Vendor();
        vendor.setCreatedAt(Instant.now());
        return vendor;
    }

    private Vendor touchTimestamps(Vendor vendor) {
        if (vendor.getCreatedAt() == null) {
            vendor.setCreatedAt(Instant.now());
        }
        vendor.setUpdatedAt(Instant.now());
        return vendor;
    }

    /**
     * Returns every vendor currently persisted in the database - backs the
     * reactive "get all vendor details" endpoint.
     */
    public Flux<VendorResponseDTO> getAllVendors() {
        return vendorRepository.findAll()
                .map(vendorMapper::toResponseDTO);
    }

    /**
     * Returns a single vendor by its business {@code vendorId}, or errors
     * with {@link VendorNotFoundException} if no such vendor is persisted.
     */
    public Mono<VendorResponseDTO> getVendorById(String vendorId) {
        return vendorRepository.findByVendorId(vendorId)
                .map(vendorMapper::toResponseDTO)
                .switchIfEmpty(Mono.error(new VendorNotFoundException(vendorId)));
    }
}

package com.vendor.integration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically triggers a vendor synchronization in the background so the
 * local database stays up to date with the external vendor system without
 * requiring a manual call to the sync endpoint. Enabled by default; disable
 * with {@code vendor.sync.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "vendor.sync.enabled", havingValue = "true", matchIfMissing = true)
public class VendorSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(VendorSyncScheduler.class);

    private final VendorIntegrationService vendorIntegrationService;

    public VendorSyncScheduler(VendorIntegrationService vendorIntegrationService) {
        this.vendorIntegrationService = vendorIntegrationService;
    }

    @Scheduled(
            initialDelayString = "${vendor.sync.initial-delay-millis:5000}",
            fixedDelayString = "${vendor.sync.fixed-delay-millis:300000}"
    )
    public void syncVendors() {
        vendorIntegrationService.syncVendors()
                .subscribe(
                        result -> log.info("Scheduled vendor sync finished: {}", result),
                        error -> log.error("Scheduled vendor sync failed", error)
                );
    }
}

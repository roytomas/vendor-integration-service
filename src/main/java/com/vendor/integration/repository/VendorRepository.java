package com.vendor.integration.repository;

import com.vendor.integration.domain.Vendor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive persistence gateway for {@link Vendor} records.
 */
public interface VendorRepository extends ReactiveCrudRepository<Vendor, Long> {

    Mono<Vendor> findByVendorId(String vendorId);
}

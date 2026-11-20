package com.vendor.integration.mockvendor;

import com.vendor.integration.client.dto.VendorApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for the external vendor's REST API, used so the integration
 * layer ({@link com.vendor.integration.client.VendorApiClient}) and its
 * resilience behaviour (timeout, retry, circuit breaker, rate limiting) can
 * be exercised and demonstrated without depending on a real third party.
 * <p>
 * By default the endpoint responds immediately and successfully. Optional
 * query parameters let callers simulate a degraded vendor system:
 * <ul>
 *     <li>{@code delayMs} - artificial latency before responding (simulate slow vendor / trigger timeout)</li>
 *     <li>{@code failureRate} - probability (0.0-1.0) of returning a 500 error (simulate vendor outages)</li>
 * </ul>
 */
@RestController
@RequestMapping("/mock-vendor")
public class MockVendorController {

    @GetMapping("/vendors")
    public Flux<VendorApiResponse> getVendors(
            @RequestParam(defaultValue = "0") long delayMs,
            @RequestParam(defaultValue = "0") double failureRate) {

        Flux<VendorApiResponse> vendors = Flux.fromIterable(MockVendorDataStore.sampleVendors());

        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            vendors = Flux.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Simulated vendor outage"));
        }

        if (delayMs > 0) {
            vendors = vendors.delaySequence(Duration.ofMillis(delayMs));
        }

        return vendors;
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/health")
    public String health() {
        return "mock vendor up";
    }
}

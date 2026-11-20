package com.vendor.integration.client;

import com.vendor.integration.client.dto.VendorApiResponse;
import com.vendor.integration.config.VendorProperties;
import com.vendor.integration.exception.VendorApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Integration-layer client responsible for talking to the external vendor
 * system. Resilience behaviour is layered on top of the raw WebClient call,
 * applied by Resilience4j's Spring AOP aspects in this fixed order (outermost
 * to innermost, regardless of the Java annotation declaration order):
 * <ol>
 *     <li>{@link Retry} (outermost) - retries transient failures with backoff</li>
 *     <li>{@link CircuitBreaker} - stops calling a failing vendor once the failure
 *         rate threshold is breached</li>
 *     <li>{@link RateLimiter} (innermost) - caps the actual call rate against the vendor</li>
 * </ol>
 * Timeout is enforced independently via the reactive {@code .timeout(Duration)}
 * operator on every attempt. Because Retry is outermost, its fallback method is
 * the single recovery point reached once retries are exhausted or an exception
 * configured as non-retryable (open circuit, rate limit rejection) propagates
 * straight through - see {@code resilience4j.retry.instances.vendorApi.ignore-exceptions}
 * in application.yml.
 */
@Component
public class VendorApiClient {

    private static final Logger log = LoggerFactory.getLogger(VendorApiClient.class);
    private static final String INSTANCE_NAME = "vendorApi";

    private final WebClient vendorWebClient;
    private final VendorProperties properties;

    public VendorApiClient(WebClient vendorWebClient, VendorProperties properties) {
        this.vendorWebClient = vendorWebClient;
        this.properties = properties;
    }

    @Retry(name = INSTANCE_NAME, fallbackMethod = "fetchVendorsFallback")
    @CircuitBreaker(name = INSTANCE_NAME)
    @RateLimiter(name = INSTANCE_NAME)
    public Flux<VendorApiResponse> fetchVendors() {
        return vendorWebClient.get()
                .uri("/vendors")
                .retrieve()
                .bodyToFlux(VendorApiResponse.class)
                .timeout(Duration.ofMillis(properties.getApi().getResponseTimeoutMillis()))
                .doOnError(e -> log.warn("Vendor API call failed: {}", e.toString()))
                .onErrorMap(this::translate);
    }

    /**
     * Fallback invoked once retries are exhausted, the circuit breaker is
     * open, or the rate limiter rejected the call. Returning an empty Flux
     * keeps the sync flow resilient to a degraded vendor system rather than
     * propagating the failure; the error is still logged for observability.
     */
    @SuppressWarnings("unused")
    private Flux<VendorApiResponse> fetchVendorsFallback(Throwable t) {
        log.error("Vendor API unavailable, returning empty result. Reason: {}", t.toString());
        return Flux.empty();
    }

    @SuppressWarnings("unused")
    private Flux<VendorApiResponse> fetchVendorsFallback(RequestNotPermitted t) {
        log.error("Vendor API call rejected by rate limiter: {}", t.toString());
        return Flux.error(new VendorApiException("Rate limit exceeded calling vendor API", t));
    }

    private Throwable translate(Throwable e) {
        if (e instanceof TimeoutException) {
            return new VendorApiException("Vendor API call timed out", e);
        }
        if (e instanceof WebClientResponseException wcre) {
            return new VendorApiException(
                    "Vendor API returned error status " + wcre.getStatusCode(), e);
        }
        if (e instanceof WebClientRequestException) {
            return new VendorApiException("Vendor API connection failure", e);
        }
        return e;
    }
}

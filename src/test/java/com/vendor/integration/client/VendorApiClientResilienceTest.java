package com.vendor.integration.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real Spring-managed {@link VendorApiClient} bean (AOP
 * proxied with Resilience4j's Retry/CircuitBreaker/RateLimiter aspects)
 * against a WireMock stub, proving the resilience behaviour works
 * end-to-end rather than just asserting the annotations are present.
 */
@SpringBootTest
@ActiveProfiles("test")
class VendorApiClientResilienceTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private VendorApiClient vendorApiClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(18089);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void vendorApiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("vendor.api.base-url", () -> "http://localhost:18089");
    }

    @BeforeEach
    void resetStubsAndCircuitBreaker() {
        wireMockServer.resetAll();
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("vendorApi");
        circuitBreaker.reset();
    }

    @Test
    void recoversAfterTransientFailuresThenSuccess() {
        wireMockServer.stubFor(get(urlEqualTo("/vendors"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second-attempt"));

        wireMockServer.stubFor(get(urlEqualTo("/vendors"))
                .inScenario("retry-then-success")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{"vendorCode":"VEND-001","vendorName":"Acme","contactEmail":"a@acme.com",
                                  "contactPhone":"+1-000","addressLine":"Addr","vendorStatus":"ACTIVE",
                                  "lastUpdated":"2025-01-01T00:00:00Z"}]
                                """)));

        StepVerifier.create(vendorApiClient.fetchVendors())
                .expectNextMatches(v -> v.vendorCode().equals("VEND-001"))
                .verifyComplete();

        wireMockServer.verify(2, WireMock.getRequestedFor(urlEqualTo("/vendors")));
    }

    @Test
    void fallsBackToEmptyWhenVendorKeepsFailing() {
        wireMockServer.stubFor(get(urlEqualTo("/vendors"))
                .willReturn(aResponse().withStatus(500)));

        StepVerifier.create(vendorApiClient.fetchVendors())
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // max-attempts=3 in application-test.yml: the initial call plus 2 retries.
        wireMockServer.verify(3, WireMock.getRequestedFor(urlEqualTo("/vendors")));
    }

    @Test
    void timesOutOnSlowVendorAndFallsBackToEmpty() {
        wireMockServer.stubFor(get(urlEqualTo("/vendors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(2000)
                        .withBody("[]")));

        StepVerifier.create(vendorApiClient.fetchVendors())
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // response-timeout-millis=300 in application-test.yml is well below the
        // stubbed 2s delay, so every attempt times out and retries are exhausted.
        assertThat(wireMockServer.getAllServeEvents()).hasSizeGreaterThanOrEqualTo(1);
    }
}

package com.vendor.integration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Builds the {@link WebClient} used by the vendor API integration layer.
 * Connect/response timeouts are enforced here at the transport level;
 * retry, circuit-breaker and rate-limiter behaviour is layered on top via
 * Resilience4j annotations on the calling client.
 */
@Configuration
@EnableConfigurationProperties(VendorProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient vendorWebClient(VendorProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) properties.getApi().getConnectTimeoutMillis())
                .responseTimeout(Duration.ofMillis(properties.getApi().getResponseTimeoutMillis()));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (properties.getApi().getApiKey() != null && !properties.getApi().getApiKey().isBlank()) {
            builder.defaultHeader("X-Api-Key", properties.getApi().getApiKey());
        }

        return builder.build();
    }
}

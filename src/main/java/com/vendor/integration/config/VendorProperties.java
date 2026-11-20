package com.vendor.integration.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Externalized configuration for the vendor integration layer:
 * base URL/credentials of the vendor system, HTTP timeouts and the
 * background sync schedule. Bound from the {@code vendor.api.*} /
 * {@code vendor.sync.*} properties in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "vendor")
public class VendorProperties {

    private final Api api = new Api();
    private final Sync sync = new Sync();

    public Api getApi() {
        return api;
    }

    public Sync getSync() {
        return sync;
    }

    public static class Api {

        @NotBlank
        private String baseUrl;

        private String apiKey;

        @Positive
        private long connectTimeoutMillis = 2000;

        @Positive
        private long responseTimeoutMillis = 3000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public long getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(long connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public long getResponseTimeoutMillis() {
            return responseTimeoutMillis;
        }

        public void setResponseTimeoutMillis(long responseTimeoutMillis) {
            this.responseTimeoutMillis = responseTimeoutMillis;
        }
    }

    public static class Sync {

        private boolean enabled = true;

        private long fixedDelayMillis = 300_000;

        private long initialDelayMillis = 5_000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMillis() {
            return fixedDelayMillis;
        }

        public void setFixedDelayMillis(long fixedDelayMillis) {
            this.fixedDelayMillis = fixedDelayMillis;
        }

        public long getInitialDelayMillis() {
            return initialDelayMillis;
        }

        public void setInitialDelayMillis(long initialDelayMillis) {
            this.initialDelayMillis = initialDelayMillis;
        }
    }
}

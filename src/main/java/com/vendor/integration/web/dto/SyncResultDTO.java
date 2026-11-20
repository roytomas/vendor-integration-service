package com.vendor.integration.web.dto;

/**
 * Outcome of an on-demand or scheduled vendor synchronization run.
 */
public record SyncResultDTO(long vendorsProcessed, String status) {
}

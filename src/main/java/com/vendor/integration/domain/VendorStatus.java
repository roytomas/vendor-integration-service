package com.vendor.integration.domain;

/**
 * Normalized status of a vendor in our domain model, mapped from the
 * free-form status string the vendor system returns.
 */
public enum VendorStatus {
    ACTIVE,
    INACTIVE,
    UNKNOWN
}

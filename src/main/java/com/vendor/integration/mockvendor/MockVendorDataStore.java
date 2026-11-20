package com.vendor.integration.mockvendor;

import com.vendor.integration.client.dto.VendorApiResponse;

import java.util.List;

/**
 * In-memory sample data standing in for a real vendor system's catalog.
 * Backs {@link MockVendorController} so the whole integration flow can be
 * exercised end-to-end without a real third-party dependency.
 */
final class MockVendorDataStore {

    private MockVendorDataStore() {
    }

    static List<VendorApiResponse> sampleVendors() {
        return List.of(
                new VendorApiResponse("VEND-001", "Acme Supplies", "sales@acmesupplies.com", "+1-212-555-0101",
                        "123 Market St, New York, NY", "ACTIVE", "2025-01-10T08:30:00Z"),
                new VendorApiResponse("VEND-002", "Globex Logistics", "contact@globexlogistics.com", "+1-312-555-0133",
                        "45 Harbor Rd, Chicago, IL", "ACTIVE", "2025-02-14T12:00:00Z"),
                new VendorApiResponse("VEND-003", "Initech Components", "support@initech.com", "+1-415-555-0199",
                        "9 Innovation Way, San Francisco, CA", "INACTIVE", "2024-11-05T16:45:00Z"),
                new VendorApiResponse("VEND-004", "Umbrella Materials", "info@umbrellamaterials.com", "+1-646-555-0177",
                        "200 Industrial Pkwy, Newark, NJ", "ACTIVE", "2025-03-01T09:15:00Z"),
                new VendorApiResponse("VEND-005", "Stark Fabrication", "orders@starkfab.com", "+1-212-555-0188",
                        "1 Fabrication Blvd, Queens, NY", "ACTIVE", "2025-03-20T14:00:00Z"),
                new VendorApiResponse("VEND-006", "Wayne Hardware", "procurement@waynehardware.com", "+1-773-555-0144",
                        "88 Gotham Ave, Chicago, IL", "A", "2024-12-18T10:30:00Z"),
                new VendorApiResponse("VEND-007", "Soylent Packaging", "hello@soylentpack.com", "+1-305-555-0166",
                        "7 Green St, Miami, FL", "DISABLED", "2024-10-02T07:20:00Z"),
                new VendorApiResponse("VEND-008", "Hooli Electronics", "biz@hooli.com", "+1-650-555-0122",
                        "500 Silicon Dr, Palo Alto, CA", "ACTIVE", "2025-04-11T11:05:00Z")
        );
    }
}

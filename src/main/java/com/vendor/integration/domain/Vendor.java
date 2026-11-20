package com.vendor.integration.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Persisted representation of a vendor, mapped from the external vendor
 * system's payload via {@link com.vendor.integration.mapper.VendorMapper}
 * and stored through the reactive {@link com.vendor.integration.repository.VendorRepository}.
 */
@Table("vendor")
public class Vendor {

    @Id
    private Long id;

    private String vendorId;

    private String name;

    private String email;

    private String phone;

    private String address;

    private VendorStatus status;

    private Instant vendorUpdatedAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    public Vendor() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public VendorStatus getStatus() {
        return status;
    }

    public void setStatus(VendorStatus status) {
        this.status = status;
    }

    public Instant getVendorUpdatedAt() {
        return vendorUpdatedAt;
    }

    public void setVendorUpdatedAt(Instant vendorUpdatedAt) {
        this.vendorUpdatedAt = vendorUpdatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

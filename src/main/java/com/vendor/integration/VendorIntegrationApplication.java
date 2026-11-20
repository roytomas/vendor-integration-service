package com.vendor.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VendorIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(VendorIntegrationApplication.class, args);
    }
}

package com.billdesk.simulator.config;

import com.billdesk.simulator.model.SimulatorSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class.
 * Reads the encryption and checksum keys from application.properties.
 * Also creates the SimulatorSettings bean (shared across the whole app).
 */
@Configuration
public class SimulatorConfig {

    // Read from application.properties - simulator.encryption.key
    @Value("${simulator.encryption.key}")
    private String encryptionKey;

    // Read from application.properties - simulator.checksum.key
    @Value("${simulator.checksum.key}")
    private String checksumKey;

    /**
     * Returns the AES-256 encryption key.
     * UAT value: q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj
     */
    public String getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * Returns the SHA-512 checksum key.
     * UAT value: union@123
     */
    public String getChecksumKey() {
        return checksumKey;
    }

    /**
     * Creates a single SimulatorSettings object for the whole application.
     * This is shared - when tester changes settings on /control page,
     * the same object is updated and PaymentService reads from it.
     */
    @Bean
    public SimulatorSettings simulatorSettings() {
        return new SimulatorSettings();
    }
}

package com.loadbalancer.config;

import java.util.ArrayList;
import java.util.List;

/*
 * Validator class that checks if a configuration is valid before starting the load balancer.
 * This prevents runtime errors by catching configuration issues early.
 */
public class ConfigValidator {
    
    /*
     * Validates the entire configuration and returns a list of error messages.
     */
    public List<String> validate(Config config) {
        // List to collect all validation errors
        List<String> errors = new ArrayList<>();

        // Validate server configuration
        if (config.getServer() == null) {
            errors.add("Server configuration is required");
        } else {
            // Check if port is in valid range (1-65535)
            if (config.getServer().getPort() <= 0 || config.getServer().getPort() > 65535) {
                errors.add("Server port must be between 1 and 65535");
            }
            // Check if host is provided
            if (config.getServer().getHost() == null || config.getServer().getHost().isEmpty()) {
                errors.add("Server host is required");
            }
        }

        // Validate backends configuration
        if (config.getBackends() == null || config.getBackends().isEmpty()) {
            errors.add("At least one backend is required");
        } else {
            // Validate each backend individually
            for (int i = 0; i < config.getBackends().size(); i++) {
                Config.BackendConfig backend = config.getBackends().get(i);
                
                // Check if backend host is provided
                if (backend.getHost() == null || backend.getHost().isEmpty()) {
                    errors.add("Backend " + i + ": host is required");
                }
                
                // Check if backend port is in valid range
                if (backend.getPort() <= 0 || backend.getPort() > 65535) {
                    errors.add("Backend " + i + ": port must be between 1 and 65535");
                }
                
                // Check if weight is at least 1 (weights must be positive)
                if (backend.getWeight() < 1) {
                    errors.add("Backend " + i + ": weight must be at least 1");
                }
            }
        }

        // Validate algorithm name (must be one of the supported algorithms)
        String algorithm = config.getAlgorithm();
        if (algorithm != null && !algorithm.matches("round-robin|least-connections|ip-hash")) {
            errors.add("Invalid algorithm: " + algorithm);
        }

        // Return all collected errors
        return errors;
    }
}

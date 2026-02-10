package com.loadbalancer.util;

/**
 * Constants class containing all default configuration values used throughout the application.
 * This centralizes configuration defaults for easy maintenance and consistency.
 */
public class Constants {
    // Default path to the YAML configuration file
    public static final String DEFAULT_CONFIG_PATH = "config.yaml";
    
    // Default port for the load balancer to listen on
    public static final int DEFAULT_PORT = 8080;
    
    // Default host address (0.0.0.0 means listen on all network interfaces)
    public static final String DEFAULT_HOST = "0.0.0.0";
    
    // Default load balancing algorithm to use
    public static final String DEFAULT_ALGORITHM = "round-robin";
    
    // Default interval (in seconds) between health checks
    public static final int DEFAULT_HEALTH_CHECK_INTERVAL = 10;
    
    // Default timeout (in seconds) for health check requests
    public static final int DEFAULT_HEALTH_CHECK_TIMEOUT = 2;
    
    // Default HTTP path to check for backend health
    public static final String DEFAULT_HEALTH_CHECK_PATH = "/health";
    
    // Number of consecutive failures before marking a backend as unhealthy
    public static final int DEFAULT_UNHEALTHY_THRESHOLD = 3;
    
    // Number of consecutive successes before marking a backend as healthy again
    public static final int DEFAULT_HEALTHY_THRESHOLD = 2;
    
    // Size of the buffer (in bytes) used for reading/writing data streams
    public static final int BUFFER_SIZE = 8192;
    
    // Private constructor to prevent instantiation of this utility class
    private Constants() {}
}

package com.loadbalancer;

import com.loadbalancer.algorithm.*;
import com.loadbalancer.config.Config;
import com.loadbalancer.health.HealthChecker;
import com.loadbalancer.server.Backend;
import com.loadbalancer.server.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * Main LoadBalancer class that orchestrates all components.
 * Manages the lifecycle of the listener, health checker, and backends.
 * This is the central coordinator that ties everything together.
 */

public class LoadBalancer {
    // Logger for load balancer events
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancer.class);

    // Configuration loaded from YAML file
    private final Config config;

    // Thread-safe list of backend servers (shared with listener and health checker)
    // CopyOnWriteArrayList allows concurrent reads without locking
    private final List<Backend> backends;

    // Listener that accepts client connections
    private Listener listener;

    // Health checker that monitors backend health
    private HealthChecker healthChecker;

    // Flag indicating if load balancer is running
    private volatile boolean running;

    /*
     * Constructor initializes the load balancer with configuration.
     * 
     * @param config Configuration object loaded from YAML
     */

    public LoadBalancer(Config config) {
        this.config = config;
        // Use CopyOnWriteArrayList for thread-safe concurrent access
        this.backends = new CopyOnWriteArrayList<>();
        this.running = false;
    }

    /*
     * Starts the load balancer and all its components.
     * Initializes backends, starts health checker, and begins accepting
     * connections.
     */

    public void start() {
        // Prevent starting if already running
        if (running) {
            logger.warn("Load balancer is already running");
            return;
        }

        logger.info("Load Balancer starting...");
        logger.info("Configuration loaded");

        // Initialize backend servers from configuration
        for (Config.BackendConfig backendConfig : config.getBackends()) {
            // Create Backend object for each configured backend
            Backend backend = new Backend(
                    backendConfig.getHost(),
                    backendConfig.getPort(),
                    backendConfig.getWeight());
            // Add to shared backends list
            backends.add(backend);
            logger.info("Registered backend: {} (weight: {})",
                    backend.getAddress(), backend.getWeight());
        }

        // Create the appropriate load balancing algorithm based on config
        LoadBalancingAlgorithm algorithm = createAlgorithm(config.getAlgorithm());
        logger.info("Using algorithm: {}", config.getAlgorithm().toUpperCase());

        // Start health checker if enabled in configuration
        if (config.getHealthCheck() != null && config.getHealthCheck().isEnabled()) {
            // Parse interval string (e.g., "10s") to seconds
            int intervalSeconds = parseTimeToSeconds(config.getHealthCheck().getInterval());

            // Create health checker with configuration
            healthChecker = new HealthChecker(
                    backends,
                    config.getHealthCheck().getPath(),
                    config.getHealthCheck().getUnhealthyThreshold(),
                    config.getHealthCheck().getHealthyThreshold());

            // Start periodic health checking
            healthChecker.start(intervalSeconds);
        }

        // Start the listener to accept client connections
        try {
            // Create listener with server configuration including thread pool size
            listener = new Listener(
                    config.getServer().getHost(),
                    config.getServer().getPort(),
                    backends,
                    algorithm,
                    config.getServer().getThreadPoolSize());

            // Mark as running
            running = true;

            // Start listener in a separate thread so it doesn't block
            new Thread(() -> {
                try {
                    // This blocks until stop() is called
                    listener.start();
                } catch (Exception e) {
                    logger.error("Listener error", e);
                    running = false;
                }
            }).start();

        } catch (Exception e) {
            logger.error("Failed to start load balancer", e);
            // Clean up if startup fails
            stop();
        }
    }

    /*
     * Stops the load balancer and cleans up all resources.
     * Gracefully shuts down listener and health checker.
     */

    public void stop() {
        // Don't try to stop if not running
        if (!running) {
            logger.warn("Load balancer is not running");
            return;
        }

        logger.info("Shutting down load balancer...");
        running = false;

        // Stop accepting new connections
        if (listener != null) {
            listener.stop();
        }

        // Stop health checking
        if (healthChecker != null) {
            healthChecker.stop();
        }

        logger.info("Load balancer stopped");
    }

    /*
     * Returns a formatted status report of the load balancer.
     * Shows listening address, algorithm, and status of all backends.
     */

    public String getStatus() {
        // Return simple message if not running
        if (!running) {
            return "Load balancer is not running";
        }

        // Build status report
        StringBuilder status = new StringBuilder();
        status.append("Load Balancer Status\n");
        status.append("====================\n");

        // Show listening address
        status.append(String.format("Listening on: %s:%d\n",
                config.getServer().getHost(), config.getServer().getPort()));

        // Show algorithm being used
        status.append(String.format("Algorithm: %s\n", config.getAlgorithm()));
        status.append("\nBackends:\n");

        // Count healthy backends
        long healthyCount = backends.stream().filter(Backend::isHealthy).count();
        status.append(String.format("Healthy: %d/%d\n\n", healthyCount, backends.size()));

        // List each backend with its status
        for (Backend backend : backends) {
            status.append(String.format("  %s - %s (connections: %d, weight: %d)\n",
                    backend.getAddress(),
                    backend.isHealthy() ? "HEALTHY" : "UNHEALTHY",
                    backend.getActiveConnections(),
                    backend.getWeight()));
        }

        return status.toString();
    }

    /*
     * Checks if the load balancer is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /*
     * Factory method to create the appropriate load balancing algorithm.
     */

    private LoadBalancingAlgorithm createAlgorithm(String algorithmName) {
        // Use switch expression to create the right algorithm
        return switch (algorithmName.toLowerCase()) {
            case "least-connections" -> new LeastConnectionsAlgorithm();
            case "ip-hash" -> new IPHashAlgorithm();
            default -> new RoundRobinAlgorithm(); // Default to round-robin
        };
    }

    /*
     * Parses a time string like "10s" into seconds.
     */

    private int parseTimeToSeconds(String time) {
        // If string ends with 's', remove it and parse the number
        if (time.endsWith("s")) {
            return Integer.parseInt(time.substring(0, time.length() - 1));
        }
        // Otherwise just parse as-is
        return Integer.parseInt(time);
    }
}

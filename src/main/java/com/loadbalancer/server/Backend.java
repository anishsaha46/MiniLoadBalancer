package com.loadbalancer.server;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents a single backend server that receives traffic from the load balancer.
 * Tracks health status, active connections, and health check results.
 * Thread-safe using atomic variables for concurrent access.
 */
public class Backend {
    // Hostname or IP address of the backend server (immutable)
    private final String host;
    
    // Port number the backend listens on (immutable)
    private final int port;
    
    // Weight for weighted load balancing algorithms (immutable)
    private final int weight;
    
    // Current health status (true = healthy, false = unhealthy)
    // AtomicBoolean ensures thread-safe reads/writes
    private final AtomicBoolean healthy;
    
    // Number of currently active connections to this backend
    // AtomicInteger allows thread-safe increment/decrement
    private final AtomicInteger activeConnections;
    
    // Counter for consecutive health check failures
    private final AtomicInteger consecutiveFailures;
    
    // Counter for consecutive health check successes
    private final AtomicInteger consecutiveSuccesses;


    public Backend(String host, int port, int weight) {
        this.host = host;
        this.port = port;
        this.weight = weight;
        // Start as healthy by default
        this.healthy = new AtomicBoolean(true);
        // Start with zero active connections
        this.activeConnections = new AtomicInteger(0);
        // Start with zero failures
        this.consecutiveFailures = new AtomicInteger(0);
        // Start with zero successes
        this.consecutiveSuccesses = new AtomicInteger(0);
    }

    // Getter methods for backend properties
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }
    
    // Get current health status (thread-safe)
    public boolean isHealthy() { return healthy.get(); }
    
    // Set health status (thread-safe)
    public void setHealthy(boolean healthy) { this.healthy.set(healthy); }
    
    // Get number of active connections (thread-safe)
    public int getActiveConnections() { return activeConnections.get(); }
    
    // Increment active connections when a new request arrives (thread-safe)
    public void incrementConnections() { activeConnections.incrementAndGet(); }
    
    // Decrement active connections when a request completes (thread-safe)
    public void decrementConnections() { activeConnections.decrementAndGet(); }
    
    // Get number of consecutive health check failures
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    
    // Increment failure counter and return new value (thread-safe)
    public int incrementFailures() { return consecutiveFailures.incrementAndGet(); }
    
    // Reset failure counter to zero (thread-safe)
    public void resetFailures() { consecutiveFailures.set(0); }
    
    // Get number of consecutive health check successes
    public int getConsecutiveSuccesses() { return consecutiveSuccesses.get(); }
    
    // Increment success counter and return new value (thread-safe)
    public int incrementSuccesses() { return consecutiveSuccesses.incrementAndGet(); }
    
    // Reset success counter to zero (thread-safe)
    public void resetSuccesses() { consecutiveSuccesses.set(0); }

    public String getAddress() {
        return host + ":" + port;
    }

    /**
     * Returns a string representation of the backend with all its properties.
     * Useful for logging and debugging.
     */
    @Override
    public String toString() {
        return String.format("Backend{%s, weight=%d, healthy=%s, connections=%d}",
                getAddress(), weight, healthy.get(), activeConnections.get());
    }
}

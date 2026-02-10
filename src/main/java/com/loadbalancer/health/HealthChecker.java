package com.loadbalancer.health;

import com.loadbalancer.server.Backend;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * Background service that periodically checks the health of backend servers.
 * Marks backends as healthy or unhealthy based on HTTP health check responses.
 * Runs on a scheduled interval using a separate thread pool.
 */
public class HealthChecker {
    // Logger for health check events
    private static final Logger logger = LoggerFactory.getLogger(HealthChecker.class);
    
    // List of backends to monitor (shared reference with load balancer)
    private final List<Backend> backends;
    
    // HTTP path to check on each backend (e.g., "/health")
    private final String healthCheckPath;
    
    // Number of consecutive failures before marking backend as unhealthy
    private final int unhealthyThreshold;
    
    // Number of consecutive successes before marking backend as healthy again
    private final int healthyThreshold;
    
    // Scheduler for running health checks at regular intervals
    private final ScheduledExecutorService scheduler;
    
    // HTTP client for making health check requests
    private final CloseableHttpClient httpClient;

    public HealthChecker(List<Backend> backends, String healthCheckPath,
                         int unhealthyThreshold, int healthyThreshold) {
        this.backends = backends;
        this.healthCheckPath = healthCheckPath;
        this.unhealthyThreshold = unhealthyThreshold;
        this.healthyThreshold = healthyThreshold;
        
        // Create a single-threaded scheduler for running health checks
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Create HTTP client for making health check requests
        this.httpClient = HttpClients.createDefault();
    }

    /*
     * Starts the health checker, scheduling periodic checks.
     */
    public void start(long intervalSeconds) {
        // Schedule checkAllBackends to run at fixed intervals
        // Initial delay = 0 (start immediately)
        // Period = intervalSeconds (repeat every N seconds)
        scheduler.scheduleAtFixedRate(this::checkAllBackends, 0, intervalSeconds, TimeUnit.SECONDS);
        logger.info("Health checker started (interval: {}s)", intervalSeconds);
    }

    /*
     * Checks the health of all backends and updates their status.
     * This method is called periodically by the scheduler.
     */
    private void checkAllBackends() {
        // Counter for healthy backends
        int healthyCount = 0;
        
        // Check each backend individually
        for (Backend backend : backends) {
            // Perform health check on this backend
            HealthCheckResult result = checkBackend(backend);
            
            // Update backend's health status based on result
            updateBackendHealth(backend, result);
            
            // Count healthy backends
            if (backend.isHealthy()) {
                healthyCount++;
            }
        }
        
        // Log summary of health check round
        logger.debug("Health check completed: {}/{} backends healthy", healthyCount, backends.size());
    }

    /*
     * Performs a single health check on a backend by sending an HTTP GET request.
     */
    private HealthCheckResult checkBackend(Backend backend) {
        // Build the health check URL (e.g., "http://localhost:3001/health")
        String url = String.format("http://%s:%d%s", backend.getHost(), backend.getPort(), healthCheckPath);
        
        // Record start time to measure response time
        long startTime = System.currentTimeMillis();

        try {
            // Create HTTP GET request
            HttpGet request = new HttpGet(url);
            
            // Execute request and process response
            return httpClient.execute(request, response -> {
                // Calculate how long the request took
                long responseTime = System.currentTimeMillis() - startTime;
                
                // Get HTTP status code (e.g., 200, 404, 500)
                int statusCode = response.getCode();
                
                // Consume response body to free resources
                EntityUtils.consume(response.getEntity());

                // Check if status code is 200 (OK)
                if (statusCode == 200) {
                    return new HealthCheckResult(true, responseTime, "OK");
                } else {
                    // Any other status code is considered unhealthy
                    return new HealthCheckResult(false, responseTime, "Status: " + statusCode);
                }
            });
        } catch (Exception e) {
            // If request fails (connection refused, timeout, etc.), mark as unhealthy
            long responseTime = System.currentTimeMillis() - startTime;
            return new HealthCheckResult(false, responseTime, e.getMessage());
        }
    }

    /*
     * Updates a backend's health status based on the health check result.
     * Uses thresholds to prevent flapping (rapid status changes).
     */
    private void updateBackendHealth(Backend backend, HealthCheckResult result) {
        if (result.isHealthy()) {
            // Health check succeeded
            
            // Reset failure counter since we got a success
            backend.resetFailures();
            
            // Increment consecutive success counter
            int successes = backend.incrementSuccesses();

            // If backend is currently marked unhealthy and has enough consecutive successes
            if (!backend.isHealthy() && successes >= healthyThreshold) {
                // Mark backend as healthy again
                backend.setHealthy(true);
                backend.resetSuccesses();
                logger.info("Backend {} recovered and marked healthy", backend.getAddress());
            }
        } else {
            // Health check failed
            
            // Reset success counter since we got a failure
            backend.resetSuccesses();
            
            // Increment consecutive failure counter
            int failures = backend.incrementFailures();

            // If backend is currently healthy and has enough consecutive failures
            if (backend.isHealthy() && failures >= unhealthyThreshold) {
                // Mark backend as unhealthy
                backend.setHealthy(false);
                logger.error("Backend {} marked unhealthy after {} consecutive failures",
                        backend.getAddress(), failures);
            } else if (failures < unhealthyThreshold) {
                // Still healthy but failing - log warning
                logger.warn("Backend {} failed health check (attempt {}/{}): {}",
                        backend.getAddress(), failures, unhealthyThreshold, result.getMessage());
            }
        }
    }

    /**
     * Stops the health checker and cleans up resources.
     * Shuts down the scheduler and closes the HTTP client.
     */
    public void stop() {
        // Initiate graceful shutdown of scheduler
        scheduler.shutdown();
        
        try {
            // Wait up to 5 seconds for scheduled tasks to complete
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                // Force shutdown if tasks don't complete in time
                scheduler.shutdownNow();
            }
            
            // Close HTTP client to free resources
            httpClient.close();
        } catch (Exception e) {
            logger.error("Error stopping health checker", e);
        }
    }
}

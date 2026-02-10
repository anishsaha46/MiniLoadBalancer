package com.loadbalancer.algorithm;

import com.loadbalancer.server.Backend;
import java.util.List;

/**
 * Least Connections load balancing algorithm.
 * Routes requests to the backend with the fewest active connections.
 * This helps balance load more evenly when requests have varying durations.
 */
public class LeastConnectionsAlgorithm implements LoadBalancingAlgorithm {
    
    @Override
    public Backend selectBackend(List<Backend> backends, String clientIp) {
        // Return null if no backends available
        if (backends.isEmpty()) {
            return null;
        }

        // Track the backend with minimum connections
        Backend selected = null;
        
        // Start with maximum possible value so first backend will be selected
        int minConnections = Integer.MAX_VALUE;

        // Iterate through all backends to find the one with fewest connections
        for (Backend backend : backends) {
            // Get current number of active connections for this backend
            int connections = backend.getActiveConnections();
            
            // If this backend has fewer connections than current minimum, select it
            if (connections < minConnections) {
                minConnections = connections;
                selected = backend;
            }
        }
        return selected;
    }
}

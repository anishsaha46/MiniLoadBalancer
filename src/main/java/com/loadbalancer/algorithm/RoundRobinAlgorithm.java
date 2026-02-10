package com.loadbalancer.algorithm;

import com.loadbalancer.server.Backend;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round Robin load balancing algorithm with weighted support.
 * Distributes requests sequentially across backends, respecting their weights.
 * Higher weight = more requests. Thread-safe using AtomicInteger.
 */
public class RoundRobinAlgorithm implements LoadBalancingAlgorithm {
    // Counter to track which backend to select next (thread-safe)
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    @Override
    public Backend selectBackend(List<Backend> backends, String clientIp) {
        // Return null if no backends available
        if (backends.isEmpty()) {
            return null;
        }

        // Calculate total weight of all backends
        // Example: if backends have weights [1, 1, 2], totalWeight = 4
        int totalWeight = backends.stream().mapToInt(Backend::getWeight).sum();
        
        // Get next index and increment counter atomically (thread-safe)
        // Modulo ensures we cycle through 0 to totalWeight-1
        int index = currentIndex.getAndIncrement() % totalWeight;

        // Find which backend this index corresponds to based on weights
        // Example: index 0 -> backend 0, index 1 -> backend 1, index 2,3 -> backend 2
        int weightSum = 0;
        for (Backend backend : backends) {
            // Add current backend's weight to running sum
            weightSum += backend.getWeight();
            
            // If our index falls within this backend's weight range, select it
            if (index < weightSum) {
                return backend;
            }
        }

        // Fallback (should never reach here, but return first backend just in case)
        return backends.get(0);
    }
}

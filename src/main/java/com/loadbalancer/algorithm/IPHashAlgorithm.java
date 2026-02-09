package com.loadbalancer.algorithm;

import com.loadbalancer.server.Backend;
import java.util.List;

/**
 * IP Hash load balancing algorithm for sticky sessions.
 * Routes requests from the same client IP to the same backend server.
 * This ensures session persistence when backends maintain session state.
 */
public class IPHashAlgorithm implements LoadBalancingAlgorithm {

    @Override
    public Backend selectBackend(List<Backend> backends, String clientIp) {
        // Return null if no backends available
        if (backends.isEmpty()) {
            return null;
        }

        // Calculate hash code of the client IP address
        // Using bitwise AND with 0x7FFFFFFF ensures a positive number
        // (Math.abs can return negative for Integer.MIN_VALUE)
        int hash = clientIp.hashCode() & 0x7FFFFFFF;

        // Use modulo to map hash to a backend index
        // This ensures the same IP always maps to the same backend
        int index = hash % backends.size();

        // Return the backend at the calculated index
        return backends.get(index);
    }
}

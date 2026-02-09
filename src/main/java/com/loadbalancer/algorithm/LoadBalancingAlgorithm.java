package com.loadbalancer.algorithm;
import com.loadbalancer.server.Backend;
import java.util.List;

public interface LoadBalancingAlgorithm {

    Backend selectBackend(List<Backend> backends,String clientIp);
    
} 
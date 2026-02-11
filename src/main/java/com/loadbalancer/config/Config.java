package com.loadbalancer.config;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class Config {

    /**
     * Configuration for the load balancer server itself.
     */
    public static class ServerConfig {
        // Port number the load balancer listens on
        private int port;

        // Host address to bind to (e.g., 0.0.0.0 for all interfaces)
        private String host;

        // Thread pool size for handling concurrent connections (default: 100)
        @JsonProperty("thread_pool_size")
        private int threadPoolSize = 100;

        // Getter for port
        public int getPort() {
            return port;
        }

        // Setter for port (used by Jackson during deserialization)
        public void setPort(int port) {
            this.port = port;
        }

        // Getter for host
        public String getHost() {
            return host;
        }

        // Setter for host (used by Jackson during deserialization)
        public void setHost(String host) {
            this.host = host;
        }

        // Getter for thread pool size
        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        // Setter for thread pool size
        public void setThreadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
        }
    }


    /**
     * Configuration for a single backend server.
     */
    public static class BackendConfig {
        // Hostname or IP address of the backend server
        private String host;

        // Port number the backend server listens on
        private int port;

        // Weight for weighted load balancing (higher = more traffic)
        private int weight = 1;

        // Getter for host
        public String getHost() {
            return host;
        }

        // Setter for host
        public void setHost(String host) {
            this.host = host;
        }

        // Getter for port
        public int getPort() {
            return port;
        }

        // Setter for port
        public void setPort(int port) {
            this.port = port;
        }

        // Getter for weight
        public int getWeight() {
            return weight;
        }

        // Setter for weight
        public void setWeight(int weight) {
            this.weight = weight;
        }
    }



        /**
     * Configuration for health checking of backend servers.
     */
    public static class HealthCheckConfig {
        // Whether health checking is enabled
        private boolean enabled = true;

        // How often to perform health checks (e.g., "10s")
        private String interval = "10s";

        // Maximum time to wait for a health check response (e.g., "2s")
        private String timeout = "2s";

        // HTTP path to check on backend servers
        private String path = "/health";

        // Number of consecutive failures before marking backend as unhealthy
        @JsonProperty("unhealthy_threshold")
        private int unhealthyThreshold = 3;

        // Number of consecutive successes before marking backend as healthy
        @JsonProperty("healthy_threshold")
        private int healthyThreshold = 2;

        // Check if health checking is enabled
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        // Get health check interval
        public String getInterval() {
            return interval;
        }

        public void setInterval(String interval) {
            this.interval = interval;
        }

        // Get health check timeout
        public String getTimeout() {
            return timeout;
        }

        public void setTimeout(String timeout) {
            this.timeout = timeout;
        }

        // Get health check path
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        // Get unhealthy threshold
        public int getUnhealthyThreshold() {
            return unhealthyThreshold;
        }

        public void setUnhealthyThreshold(int unhealthyThreshold) {
            this.unhealthyThreshold = unhealthyThreshold;
        }

        // Get healthy threshold
        public int getHealthyThreshold() {
            return healthyThreshold;
        }

        public void setHealthyThreshold(int healthyThreshold) {
            this.healthyThreshold = healthyThreshold;
        }
    }

    
}

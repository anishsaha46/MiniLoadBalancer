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

    
}

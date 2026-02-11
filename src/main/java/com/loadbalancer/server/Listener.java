package com.loadbalancer.server;

import com.loadbalancer.algorithm.LoadBalancingAlgorithm;
import com.loadbalancer.proxy.ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 * Listener accepts incoming HTTP connections and delegates them to
 * ProxyHandler.
 * Runs a ServerSocket that listens for client connections on the configured
 * port.
 * Uses a configurable thread pool to handle multiple concurrent connections.
 * 
 * This class:
 * 1. Binds to the configured host:port address
 * 2. Accepts incoming client connections in a loop
 * 3. Submits each connection to a thread pool for processing
 * 4. Gracefully shuts down when stop() is called
 */
public class Listener {
    // Logger for listener events
    private static final Logger logger = LoggerFactory.getLogger(Listener.class);

    // Default thread pool size if not configured
    private static final int DEFAULT_THREAD_POOL_SIZE = 100;

    // Default backlog for pending connections
    private static final int CONNECTION_BACKLOG = 50;

    // Host address to bind to
    private final String host;

    // Port number to listen on
    private final int port;

    // List of backend servers (shared with health checker)
    private final List<Backend> backends;

    // Load balancing algorithm to use for selecting backends
    private final LoadBalancingAlgorithm algorithm;

    // Thread pool for handling client connections concurrently
    private final ExecutorService executorService;

    // Server socket that accepts incoming connections
    private ServerSocket serverSocket;

    // Flag to control the listener loop (volatile for thread visibility)
    private volatile boolean running;

    /*
     * Constructor initializes the listener with configuration and default thread
     * pool.
     */
    public Listener(String host, int port, List<Backend> backends, LoadBalancingAlgorithm algorithm) {
        this(host, port, backends, algorithm, DEFAULT_THREAD_POOL_SIZE);
    }

    /*
     * Constructor initializes the listener with configuration and custom thread
     * pool size.
     */
    public Listener(String host, int port, List<Backend> backends,
            LoadBalancingAlgorithm algorithm, int threadPoolSize) {
        this.host = host;
        this.port = port;
        this.backends = backends;
        this.algorithm = algorithm;

        // Create thread pool with configured size to handle concurrent connections
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        logger.debug("Created thread pool with {} threads", threadPoolSize);

        // Start in stopped state
        this.running = false;
    }

    /*
     * Starts the listener and begins accepting client connections.
     * This method blocks until stop() is called.
     * 
     * @throws IOException If unable to bind to the port
     */
    public void start() throws IOException {
        // Create server socket and bind to specific host:port
        serverSocket = new ServerSocket();

        // Resolve host address (0.0.0.0 = all interfaces)
        InetAddress bindAddress = InetAddress.getByName(host);

        // Bind to the socket address with specified backlog
        serverSocket.bind(new InetSocketAddress(bindAddress, port), CONNECTION_BACKLOG);

        // Mark as running
        running = true;
        logger.info("Listening on {}:{}", host, port);

        // Main accept loop - runs until stop() is called
        while (running) {
            try {
                // Wait for and accept a client connection (blocking call)
                Socket clientSocket = serverSocket.accept();

                // Submit the connection to thread pool for handling
                // ProxyHandler will forward the request to a backend
                executorService.submit(new ProxyHandler(clientSocket, backends, algorithm));
            } catch (IOException e) {
                // Only log error if we're still supposed to be running
                // (closing the socket throws IOException, which is expected during shutdown)
                if (running) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    /*
     * Stops the listener and cleans up resources.
     * Closes the server socket and shuts down the thread pool.
     */
    public void stop() {
        // Set running flag to false to exit accept loop
        running = false;

        try {
            // Close server socket to stop accepting new connections
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Initiate graceful shutdown of thread pool
            executorService.shutdown();

            // Wait up to 10 seconds for active connections to complete
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                // Force shutdown if tasks don't complete in time
                executorService.shutdownNow();
            }

            logger.info("Listener stopped");
        } catch (Exception e) {
            logger.error("Error stopping listener", e);
        }
    }
}

package com.loadbalancer.proxy;

import com.loadbalancer.algorithm.LoadBalancingAlgorithm;
import com.loadbalancer.server.Backend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

/*
 * ProxyHandler forwards HTTP requests from clients to backend servers.
 * Each instance handles a single client connection in a separate thread.
 * Implements Runnable so it can be executed by the thread pool.
 * 
 * This handler:
 * 1. Receives an HTTP request from a client
 * 2. Uses the load balancing algorithm to select a healthy backend
 * 3. Forwards the request to the backend and returns the response
 * 4. Properly handles Content-Length and chunked transfer encoding
 */
public class ProxyHandler implements Runnable {
    // Logger for proxy events
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);

    // Socket connected to the client
    private final Socket clientSocket;

    // List of all backend servers
    private final List<Backend> backends;

    // Algorithm for selecting which backend to use
    private final LoadBalancingAlgorithm algorithm;

    // Connection timeout in milliseconds (for connecting to backend)
    private static final int CONNECTION_TIMEOUT = 3000;

    // Read timeout in milliseconds (for reading from backend)
    private static final int READ_TIMEOUT = 30000;

    // Buffer size for data transfer (8KB)
    private static final int BUFFER_SIZE = 8192;

    /**
     * Constructor initializes the proxy handler for a client connection.
     * 
     * @param clientSocket Socket connected to the client
     * @param backends     List of all backend servers
     * @param algorithm    Load balancing algorithm to use
     */
    public ProxyHandler(Socket clientSocket, List<Backend> backends, LoadBalancingAlgorithm algorithm) {
        this.clientSocket = clientSocket;
        this.backends = backends;
        this.algorithm = algorithm;
    }

    /*
     * Main execution method called by the thread pool.
     * Handles the request and ensures cleanup happens.
     */
    @Override
    public void run() {
        try {
            // Process the client request
            handleRequest();
        } catch (SocketTimeoutException e) {
            logger.warn("Request timeout: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error handling request: {}", e.getMessage());
        } finally {
            // Always close the client socket when done
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    /*
     * Handles a single client request by selecting a backend and forwarding.
     * 
     * @throws IOException If there's an error communicating with client or backend
     */
    private void handleRequest() throws IOException {
        // Filter to get only healthy backends
        List<Backend> healthyBackends = backends.stream()
                .filter(Backend::isHealthy)
                .collect(Collectors.toList());

        // Check if any healthy backends are available
        if (healthyBackends.isEmpty()) {
            logger.error("No healthy backends available");
            sendErrorResponse(clientSocket, 503, "Service Unavailable");
            return;
        }

        // Get client's IP address for IP-hash algorithm
        String clientIp = clientSocket.getInetAddress().getHostAddress();

        // Use algorithm to select which backend to use
        Backend backend = algorithm.selectBackend(healthyBackends, clientIp);

        // Check if backend selection succeeded
        if (backend == null) {
            logger.error("Failed to select backend");
            sendErrorResponse(clientSocket, 503, "Service Unavailable");
            return;
        }

        // Increment connection counter for this backend
        backend.incrementConnections();
        try {
            // Forward the request to the selected backend
            forwardRequest(backend);
            logger.debug("Request routed to {}", backend.getAddress());
        } finally {
            // Always decrement connection counter when done
            backend.decrementConnections();
        }
    }

    /*
     * Forwards the HTTP request from client to backend and returns the response.
     * Properly parses HTTP headers to handle Content-Length and chunked encoding.
     * 
     * @param backend The backend server to forward to
     * @throws IOException If there's an error during forwarding
     */
    private void forwardRequest(Backend backend) throws IOException {
        // Create backend socket with connection timeout
        Socket backendSocket = new Socket();
        try {
            // Connect with timeout to prevent hanging on unresponsive backends
            backendSocket.connect(
                    new InetSocketAddress(backend.getHost(), backend.getPort()),
                    CONNECTION_TIMEOUT);
            // Set read timeout for backend responses
            backendSocket.setSoTimeout(READ_TIMEOUT);

            // Get streams for all sockets
            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();
            InputStream backendIn = backendSocket.getInputStream();
            OutputStream backendOut = backendSocket.getOutputStream();

            // Forward request from client to backend
            forwardHttpMessage(clientIn, backendOut, true);

            // Forward response from backend to client
            forwardHttpMessage(backendIn, clientOut, false);

        } finally {
            // Close backend socket
            try {
                backendSocket.close();
            } catch (IOException e) {
                logger.debug("Error closing backend socket", e);
            }
        }
    }

    /*
     * Forwards a complete HTTP message (request or response) from input to output.
     * Parses headers to properly handle Content-Length and chunked transfer
     * encoding.
     * 
     * @param input     Source input stream
     * @param output    Destination output stream
     * @param isRequest true if forwarding a request, false for response
     * @throws IOException If there's an error during transfer
     */
    private void forwardHttpMessage(InputStream input, OutputStream output, boolean isRequest)
            throws IOException {
        BufferedInputStream bufferedInput = new BufferedInputStream(input, BUFFER_SIZE);

        // Read and forward headers, getting content info
        HttpHeaderInfo headerInfo = readAndForwardHeaders(bufferedInput, output);

        // Forward body based on transfer encoding
        if (headerInfo.isChunked) {
            // Chunked transfer encoding - read chunks until terminator
            forwardChunkedBody(bufferedInput, output);
        } else if (headerInfo.contentLength > 0) {
            // Content-Length specified - read exact number of bytes
            forwardFixedLengthBody(bufferedInput, output, headerInfo.contentLength);
        } else if (headerInfo.contentLength == -1 && !isRequest) {
            // Response with no Content-Length (connection: close) - read until EOF
            forwardUntilEof(bufferedInput, output);
        }
        // If contentLength == 0 or request with no body, nothing more to do

        output.flush();
    }

    /*
     * Reads HTTP headers line by line and forwards them to output.
     * Extracts Content-Length and Transfer-Encoding values.
     * 
     * @param input  Buffered input stream
     * @param output Output stream
     * @return HttpHeaderInfo containing content length and chunk encoding status
     * @throws IOException If there's an error reading/writing
     */
    private HttpHeaderInfo readAndForwardHeaders(BufferedInputStream input, OutputStream output)
            throws IOException {
        HttpHeaderInfo info = new HttpHeaderInfo();
        StringBuilder lineBuilder = new StringBuilder();
        boolean headersComplete = false;

        // Read headers line by line until empty line (end of headers)
        while (!headersComplete) {
            int b = input.read();
            if (b == -1) {
                break; // End of stream
            }

            output.write(b);

            if (b == '\n') {
                String line = lineBuilder.toString().trim();

                if (line.isEmpty()) {
                    // Empty line = end of headers
                    headersComplete = true;
                } else {
                    // Parse header for content info
                    String lowerLine = line.toLowerCase();
                    if (lowerLine.startsWith("content-length:")) {
                        try {
                            info.contentLength = Long.parseLong(line.substring(15).trim());
                        } catch (NumberFormatException e) {
                            logger.debug("Invalid Content-Length header: {}", line);
                        }
                    } else if (lowerLine.startsWith("transfer-encoding:")
                            && lowerLine.contains("chunked")) {
                        info.isChunked = true;
                    }
                }
                lineBuilder = new StringBuilder();
            } else if (b != '\r') {
                lineBuilder.append((char) b);
            }
        }

        return info;
    }

    /*
     * Forwards a fixed-length body based on Content-Length header.
     * 
     * @param input         Source input stream
     * @param output        Destination output stream
     * @param contentLength Number of bytes to read
     * @throws IOException If there's an error during transfer
     */
    private void forwardFixedLengthBody(InputStream input, OutputStream output, long contentLength)
            throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = contentLength;

        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int bytesRead = input.read(buffer, 0, toRead);
            if (bytesRead == -1) {
                break; // Unexpected end of stream
            }
            output.write(buffer, 0, bytesRead);
            remaining -= bytesRead;
        }
    }

    /*
     * Forwards chunked transfer encoded body.
     * Reads chunk size, chunk data, and forwards until final 0-size chunk.
     * 
     * @param input  Source input stream
     * @param output Destination output stream
     * @throws IOException If there's an error during transfer
     */
    private void forwardChunkedBody(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (true) {
            // Read chunk size line
            String sizeLine = readLine(input, output);
            if (sizeLine == null || sizeLine.isEmpty()) {
                break;
            }

            // Parse chunk size (hex)
            int chunkSize;
            try {
                // Handle extensions after semicolon
                int semiIndex = sizeLine.indexOf(';');
                String sizeStr = semiIndex >= 0 ? sizeLine.substring(0, semiIndex) : sizeLine;
                chunkSize = Integer.parseInt(sizeStr.trim(), 16);
            } catch (NumberFormatException e) {
                logger.debug("Invalid chunk size: {}", sizeLine);
                break;
            }

            if (chunkSize == 0) {
                // Final chunk - read trailing headers/CRLF
                readLine(input, output);
                break;
            }

            // Read and forward chunk data
            int remaining = chunkSize;
            while (remaining > 0) {
                int toRead = Math.min(buffer.length, remaining);
                int bytesRead = input.read(buffer, 0, toRead);
                if (bytesRead == -1) {
                    break;
                }
                output.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }

            // Read trailing CRLF after chunk data
            readLine(input, output);
        }
    }

    /*
     * Forwards data until end of stream (for responses without Content-Length).
     * 
     * @param input  Source input stream
     * @param output Destination output stream
     * @throws IOException If there's an error during transfer
     */
    private void forwardUntilEof(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    /*
     * Reads a line from input and writes it to output.
     * 
     * @param input  Source input stream
     * @param output Destination output stream
     * @return The line read (without CRLF)
     * @throws IOException If there's an error during read/write
     */
    private String readLine(InputStream input, OutputStream output) throws IOException {
        StringBuilder line = new StringBuilder();
        int b;
        while ((b = input.read()) != -1) {
            output.write(b);
            if (b == '\n') {
                break;
            } else if (b != '\r') {
                line.append((char) b);
            }
        }
        return line.toString();
    }

    /*
     * Sends an HTTP error response to the client.
     * Used when no backends are available or an error occurs.
     * 
     * @param socket     Client socket to send response to
     * @param statusCode HTTP status code (e.g., 503)
     * @param message    Error message to include in response
     */
    private void sendErrorResponse(Socket socket, int statusCode, String message) {
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            // Send HTTP status line
            out.println("HTTP/1.1 " + statusCode + " " + message);

            // Send headers
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + message.length());
            out.println("Connection: close");

            // Empty line separates headers from body
            out.println();

            // Send error message in body
            out.print(message);
        } catch (IOException e) {
            logger.error("Error sending error response", e);
        }
    }

    /*
     * Helper class to hold HTTP header information extracted during parsing.
     */
    private static class HttpHeaderInfo {
        // Content-Length value (-1 if not specified)
        long contentLength = -1;
        // Whether Transfer-Encoding: chunked is set
        boolean isChunked = false;
    }
}

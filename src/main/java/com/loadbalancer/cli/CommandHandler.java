package com.loadbalancer.cli;

import com.loadbalancer.LoadBalancer;
import com.loadbalancer.config.Config;
import com.loadbalancer.config.ConfigLoader;
import com.loadbalancer.config.ConfigValidator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CommandHandler processes CLI commands for the load balancer.
 * Uses Picocli framework for command-line parsing and execution.
 * Supports commands: start, stop, status, validate.
 */
@Command(name = "loadbalancer", mixinStandardHelpOptions = true, version = "1.0.0",
        description = "Custom Load Balancer - Java CLI Implementation")
public class CommandHandler implements Callable<Integer> {
    // Logger for CLI operations
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    
    // Static reference to the running load balancer instance
    // Shared across commands so 'stop' and 'status' can access it
    private static LoadBalancer loadBalancer;

    /**
     * Start command - starts the load balancer with the specified configuration.
     * This command blocks until the load balancer is stopped (Ctrl+C).
     * 
     * @param configPath Path to YAML configuration file
     * @return Exit code (0 = success, 1 = error)
     */
    @Command(name = "start", description = "Start the load balancer")
    public int start(@Option(names = {"-c", "--config"}, description = "Configuration file path",
            defaultValue = "config.yaml") String configPath) {
        try {
            // Load configuration from YAML file
            ConfigLoader loader = new ConfigLoader();
            Config config = loader.load(configPath);

            // Validate configuration before starting
            ConfigValidator validator = new ConfigValidator();
            List<String> errors = validator.validate(config);
            
            // If validation fails, print errors and exit
            if (!errors.isEmpty()) {
                System.err.println("Configuration validation failed:");
                errors.forEach(error -> System.err.println("  - " + error));
                return 1; // Return error code
            }

            // Create and start the load balancer
            loadBalancer = new LoadBalancer(config);
            loadBalancer.start();

            // Register shutdown hook to gracefully stop on Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (loadBalancer != null) {
                    loadBalancer.stop();
                }
            }));

            // Wait indefinitely (until interrupted by Ctrl+C)
            Thread.currentThread().join();
            return 0; // Return success code

        } catch (Exception e) {
            // Log and print error if startup fails
            logger.error("Failed to start load balancer", e);
            System.err.println("Error: " + e.getMessage());
            return 1; // Return error code
        }
    }

    /**
     * Stop command - gracefully stops the running load balancer.
     * 
     * @return Exit code (0 = success, 1 = not running)
     */
    @Command(name = "stop", description = "Stop the load balancer")
    public int stop() {
        // Check if load balancer is running
        if (loadBalancer != null && loadBalancer.isRunning()) {
            // Stop the load balancer
            loadBalancer.stop();
            System.out.println("Load balancer stopped");
            return 0; // Success
        } else {
            // Not running - nothing to stop
            System.out.println("Load balancer is not running");
            return 1; // Error
        }
    }

    /**
     * Status command - displays current status of the load balancer.
     * Shows listening address, algorithm, and health of all backends.
     * 
     * @return Exit code (0 = success, 1 = not running)
     */
    @Command(name = "status", description = "Show load balancer status")
    public int status() {
        // Check if load balancer is running
        if (loadBalancer != null && loadBalancer.isRunning()) {
            // Print detailed status report
            System.out.println(loadBalancer.getStatus());
            return 0; // Success
        } else {
            // Not running - can't show status
            System.out.println("Load balancer is not running");
            return 1; // Error
        }
    }

    /**
     * Validate command - checks if a configuration file is valid.
     * Useful for testing configuration changes before restarting.
     * 
     * @param configPath Path to YAML configuration file
     * @return Exit code (0 = valid, 1 = invalid)
     */
    @Command(name = "validate", description = "Validate configuration file")
    public int validate(@Option(names = {"-c", "--config"}, description = "Configuration file path",
            defaultValue = "config.yaml") String configPath) {
        try {
            // Check if file exists
            File configFile = new File(configPath);
            if (!configFile.exists()) {
                System.err.println("Configuration file not found: " + configPath);
                return 1; // Error
            }

            // Load configuration from file
            ConfigLoader loader = new ConfigLoader();
            Config config = loader.load(configPath);

            // Validate the configuration
            ConfigValidator validator = new ConfigValidator();
            List<String> errors = validator.validate(config);

            // Check validation results
            if (errors.isEmpty()) {
                // Configuration is valid
                System.out.println("Configuration is valid");
                return 0; // Success
            } else {
                // Configuration has errors - print them
                System.err.println("Configuration validation failed:");
                errors.forEach(error -> System.err.println("  - " + error));
                return 1; // Error
            }
        } catch (Exception e) {
            // Error loading or parsing configuration
            System.err.println("Error validating configuration: " + e.getMessage());
            return 1; // Error
        }
    }

    /**
     * Default command when no subcommand is specified.
     * Prints usage information.
     * 
     * @return Exit code (always 0)
     */
    @Override
    public Integer call() {
        // Print help/usage information
        CommandLine.usage(this, System.out);
        return 0;
    }
}

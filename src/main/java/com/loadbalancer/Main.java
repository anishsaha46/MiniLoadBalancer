package com.loadbalancer;

import com.loadbalancer.cli.CommandHandler;
import picocli.CommandLine;

/**
 * Main entry point for the load balancer application.
 * Sets up the CLI framework and executes commands.
 * 
 * This class serves as the application bootstrap that:
 * 1. Creates the CommandHandler with all CLI commands
 * 2. Configures picocli to parse command-line arguments
 * 3. Executes the appropriate command based on user input
 */
public class Main {
    /**
     * Main method - entry point when running the JAR.
     * 
     * @param args Command-line arguments (e.g., "start --config config.yaml")
     */
    public static void main(String[] args) {
        // Create the command handler that contains all CLI commands
        // CommandHandler uses @Command annotations to define subcommands
        CommandHandler commandHandler = new CommandHandler();

        // Create Picocli CommandLine with our handler
        // Picocli automatically discovers @Command-annotated methods as subcommands
        CommandLine commandLine = new CommandLine(commandHandler);

        // Execute the command and get exit code (0 = success, non-zero = error)
        // Picocli parses args and routes to the appropriate subcommand method
        int exitCode = commandLine.execute(args);

        // Exit with the appropriate code
        System.exit(exitCode);
    }
}

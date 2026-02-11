package com.loadbalancer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;
import java.io.IOException;

/*
 * Loads and parses YAML configuration files into Config objects.
 * Uses Jackson library to deserialize YAML into Java objects.
 */
public class ConfigLoader {
    // Jackson ObjectMapper configured for YAML parsing
    private final ObjectMapper mapper;

    /**
     * Constructor initializes the YAML parser.
     */
    public ConfigLoader() {
        // Create ObjectMapper with YAML factory for parsing YAML files
        this.mapper = new ObjectMapper(new YAMLFactory());
    }

    /*
     * Loads a configuration file from the specified path.
     */
    public Config load(String configPath) throws IOException {
        // Create File object from the path
        File configFile = new File(configPath);
        
        // Check if file exists before attempting to read
        if (!configFile.exists()) {
            throw new IOException("Configuration file not found: " + configPath);
        }
        
        // Parse YAML file into Config object and return
        return mapper.readValue(configFile, Config.class);
    }
}

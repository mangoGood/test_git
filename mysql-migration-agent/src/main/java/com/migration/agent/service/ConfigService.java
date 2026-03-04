package com.migration.agent.service;

import com.migration.agent.model.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static final String CONFIG_FILE = "config.properties";
    
    public void updateConfig(TaskMessage taskMessage) throws IOException {
        logger.info("Updating config file for task: {}", taskMessage.getTaskId());
        
        Properties props = new Properties();
        
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                props.load(input);
            }
        }
        
        TaskMessage.DatabaseConfig source = taskMessage.getSource();
        if (source != null) {
            props.setProperty("source.db.host", source.getHost());
            props.setProperty("source.db.port", String.valueOf(source.getPort()));
            props.setProperty("source.db.database", source.getDatabase());
            props.setProperty("source.db.username", source.getUsername());
            props.setProperty("source.db.password", source.getPassword());
        }
        
        TaskMessage.DatabaseConfig target = taskMessage.getTarget();
        if (target != null) {
            props.setProperty("target.db.host", target.getHost());
            props.setProperty("target.db.port", String.valueOf(target.getPort()));
            props.setProperty("target.db.database", target.getDatabase());
            props.setProperty("target.db.username", target.getUsername());
            props.setProperty("target.db.password", target.getPassword());
        }
        
        try (OutputStream output = new FileOutputStream(configFile)) {
            props.store(output, "Updated by Migration Agent for task: " + taskMessage.getTaskId());
        }
        
        logger.info("Config file updated successfully for task: {}", taskMessage.getTaskId());
    }
}
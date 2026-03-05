package com.migration.agent.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConnectionStringParser {
    private static final Pattern CONNECTION_PATTERN = 
        Pattern.compile("mysql://([^:]+):([^@]+)@([^:]+):(\\d+)/(.+)");
    
    public static class ConnectionInfo {
        private String host;
        private int port;
        private String database;
        private String username;
        private String password;
        
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
        }
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
        
        public String getDatabase() {
            return database;
        }
        
        public void setDatabase(String database) {
            this.database = database;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        @Override
        public String toString() {
            return String.format("mysql://%s:***@%s:%d/%s", username, host, port, database);
        }
    }
    
    public static ConnectionInfo parse(String connectionString) {
        if (connectionString == null || connectionString.trim().isEmpty()) {
            return null;
        }
        
        Matcher matcher = CONNECTION_PATTERN.matcher(connectionString.trim());
        
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid connection string format. Expected: mysql://user:pass@host:port/db");
        }
        
        ConnectionInfo info = new ConnectionInfo();
        info.setUsername(matcher.group(1));
        info.setPassword(matcher.group(2));
        info.setHost(matcher.group(3));
        info.setPort(Integer.parseInt(matcher.group(4)));
        info.setDatabase(matcher.group(5));
        
        return info;
    }
}

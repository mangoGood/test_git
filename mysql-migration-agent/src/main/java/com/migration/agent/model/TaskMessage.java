package com.migration.agent.model;

public class TaskMessage {
    private String taskId;
    private DatabaseConfig source;
    private DatabaseConfig target;
    private String migrationMode;

    public static class DatabaseConfig {
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
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public DatabaseConfig getSource() {
        return source;
    }

    public void setSource(DatabaseConfig source) {
        this.source = source;
    }

    public DatabaseConfig getTarget() {
        return target;
    }

    public void setTarget(DatabaseConfig target) {
        this.target = target;
    }

    public String getMigrationMode() {
        return migrationMode;
    }

    public void setMigrationMode(String migrationMode) {
        this.migrationMode = migrationMode;
    }
}
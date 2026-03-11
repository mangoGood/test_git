package com.synctask.service;

import com.synctask.dto.DatabaseInfo;
import com.synctask.dto.TableInfo;
import com.synctask.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

    private static final Pattern CONNECTION_PATTERN = Pattern.compile(
        "mysql://([^:]+):([^@]+)@([^:]+):(\\d+)(?:/(.*))?"
    );

    public static class ParsedConnection {
        public String username;
        public String password;
        public String host;
        public int port;
        public String database;

        public ParsedConnection(String username, String password, String host, int port, String database) {
            this.username = username;
            this.password = password;
            this.host = host;
            this.port = port;
            this.database = database;
        }
    }

    public ParsedConnection parseConnection(String connectionStr) {
        if (connectionStr == null || connectionStr.isEmpty()) {
            throw new IllegalArgumentException("连接串不能为空");
        }

        Matcher matcher = CONNECTION_PATTERN.matcher(connectionStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("连接串格式不正确，正确格式: mysql://user:pass@host:port/db");
        }

        String username = matcher.group(1);
        String password = matcher.group(2);
        String host = matcher.group(3);
        int port = Integer.parseInt(matcher.group(4));
        String database = matcher.group(5);

        return new ParsedConnection(username, password, host, port, database);
    }

    private String buildJdbcUrl(String host, int port, String database) {
        if (database != null && !database.isEmpty()) {
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true", host, port, database);
        }
        return String.format("jdbc:mysql://%s:%d/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true", host, port);
    }

    public boolean testConnection(String connectionStr) {
        ParsedConnection conn = parseConnection(connectionStr);
        
        try (Connection connection = DriverManager.getConnection(
                buildJdbcUrl(conn.host, conn.port, conn.database),
                conn.username, conn.password)) {
            
            return connection.isValid(5);
        } catch (SQLException e) {
            logger.error("测试连接失败: {}", e.getMessage());
            return false;
        }
    }

    public List<String> listDatabases(String connectionStr) {
        ParsedConnection conn = parseConnection(connectionStr);
        
        List<String> databases = new ArrayList<>();
        
        try (Connection connection = DriverManager.getConnection(
                buildJdbcUrl(conn.host, conn.port, null),
                conn.username, conn.password)) {
            
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getCatalogs();
            
            while (rs.next()) {
                String dbName = rs.getString("TABLE_CAT");
                if (!isSystemDatabase(dbName)) {
                    databases.add(dbName);
                }
            }
            
            logger.info("查询到 {} 个数据库", databases.size());
        } catch (SQLException e) {
            logger.error("查询数据库列表失败: {}", e.getMessage());
            throw new RuntimeException("查询数据库列表失败: " + e.getMessage());
        }
        
        return databases;
    }

    private boolean isSystemDatabase(String dbName) {
        return "information_schema".equalsIgnoreCase(dbName) ||
               "mysql".equalsIgnoreCase(dbName) ||
               "performance_schema".equalsIgnoreCase(dbName) ||
               "sys".equalsIgnoreCase(dbName);
    }

    public List<TableInfo> listTables(String connectionStr, String database) {
        ParsedConnection conn = parseConnection(connectionStr);
        
        List<TableInfo> tables = new ArrayList<>();
        
        try (Connection connection = DriverManager.getConnection(
                buildJdbcUrl(conn.host, conn.port, database),
                conn.username, conn.password)) {
            
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getTables(database, null, "%", new String[]{"TABLE"});
            
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                long rows = getRowCount(connection, database, tableName);
                String size = getTableSize(connection, database, tableName);
                String engine = getTableEngine(metaData, database, tableName);
                
                tables.add(new TableInfo(tableName, rows, size, engine));
            }
            
            logger.info("数据库 {} 查询到 {} 个表", database, tables.size());
        } catch (SQLException e) {
            logger.error("查询表列表失败: {}", e.getMessage());
            throw new RuntimeException("查询表列表失败: " + e.getMessage());
        }
        
        return tables;
    }

    private long getRowCount(Connection connection, String database, String tableName) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.warn("获取表 {} 行数失败: {}", tableName, e.getMessage());
        }
        return 0;
    }

    private String getTableSize(Connection connection, String database, String tableName) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT ROUND(data_length + index_length) as size_bytes " +
                 "FROM information_schema.tables " +
                 "WHERE table_schema = '" + database + "' AND table_name = '" + tableName + "'")) {
            if (rs.next()) {
                long bytes = rs.getLong("size_bytes");
                return formatSize(bytes);
            }
        } catch (SQLException e) {
            logger.warn("获取表 {} 大小失败: {}", tableName, e.getMessage());
        }
        return "0 B";
    }

    private String getTableEngine(DatabaseMetaData metaData, String database, String tableName) {
        try (ResultSet rs = metaData.getTables(database, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return rs.getString("TABLE_TYPE");
            }
        } catch (SQLException e) {
            logger.warn("获取表 {} 引擎失败: {}", tableName, e.getMessage());
        }
        return "UNKNOWN";
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    public DatabaseInfo getDatabaseWithTables(String connectionStr, String database) {
        DatabaseInfo dbInfo = new DatabaseInfo(database);
        
        try {
            List<TableInfo> tables = listTables(connectionStr, database);
            dbInfo.setTables(tables);
            dbInfo.setAccessible(true);
        } catch (Exception e) {
            dbInfo.setAccessible(false);
            dbInfo.setErrorMessage(e.getMessage());
            logger.error("获取数据库 {} 信息失败: {}", database, e.getMessage());
        }
        
        return dbInfo;
    }

    public ValidationResult validateForMigration(String sourceConnection, String targetConnection, String migrationMode) {
        ValidationResult result = new ValidationResult();
        
        ParsedConnection sourceConn = parseConnection(sourceConnection);
        ParsedConnection targetConn = parseConnection(targetConnection);
        
        try (Connection sourceDb = DriverManager.getConnection(
                buildJdbcUrl(sourceConn.host, sourceConn.port, sourceConn.database),
                sourceConn.username, sourceConn.password);
             Connection targetDb = DriverManager.getConnection(
                buildJdbcUrl(targetConn.host, targetConn.port, targetConn.database),
                targetConn.username, targetConn.password)) {
            
            String sourceVersion = getMySQLVersion(sourceDb);
            String targetVersion = getMySQLVersion(targetDb);
            
            if ("fullAndIncre".equals(migrationMode) || "increment".equals(migrationMode)) {
                checkBinlogEnabled(sourceDb, result);
                checkBinlogFormat(sourceDb, result);
                checkBinlogRowImage(sourceDb, result);
                checkServerId(sourceDb, sourceVersion, result);
            }
            
            checkVersionCompatibility(sourceVersion, targetVersion, result);
            checkSqlModeCompatibility(sourceDb, targetDb, result);
            checkSourcePermissions(sourceDb, migrationMode, result);
            checkTargetPermissions(targetDb, targetVersion, result);
            
        } catch (SQLException e) {
            logger.error("数据库校验失败: {}", e.getMessage());
            result.addItem("连接检查", "数据库连接检查", false, "连接失败: " + e.getMessage(), "error");
        }
        
        boolean allPassed = result.getCheckItems().stream().allMatch(ValidationResult.CheckItem::isPassed);
        result.setAllPassed(allPassed);
        
        return result;
    }

    private String getMySQLVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return "unknown";
    }

    private void checkBinlogEnabled(Connection conn, ValidationResult result) {
        try {
            String logBin = getVariable(conn, "log_bin");
            boolean passed = "ON".equalsIgnoreCase(logBin) || "1".equals(logBin);
            String message = passed ? "Binlog已开启" : "Binlog未开启，增量同步需要开启binlog";
            result.addItem("Binlog开启状态", "增量同步需要源数据库开启binlog", passed, message, "error");
        } catch (SQLException e) {
            result.addItem("Binlog开启状态", "增量同步需要源数据库开启binlog", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private void checkBinlogFormat(Connection conn, ValidationResult result) {
        try {
            String format = getVariable(conn, "binlog_format");
            boolean passed = "ROW".equalsIgnoreCase(format);
            String message = passed ? "Binlog格式为ROW" : "当前Binlog格式为" + format + "，需要设置为ROW";
            result.addItem("Binlog格式", "增量同步需要Binlog格式为ROW", passed, message, "error");
        } catch (SQLException e) {
            result.addItem("Binlog格式", "增量同步需要Binlog格式为ROW", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private void checkBinlogRowImage(Connection conn, ValidationResult result) {
        try {
            String rowImage = getVariable(conn, "binlog_row_image");
            if (rowImage == null || rowImage.isEmpty()) {
                result.addItem("Binlog Row Image", "binlog_row_image需设置为FULL", true, "参数不存在(可能是MySQL 5.5及以下版本)，跳过检查", "warning");
                return;
            }
            boolean passed = "FULL".equalsIgnoreCase(rowImage);
            String message = passed ? "binlog_row_image为FULL" : "当前binlog_row_image为" + rowImage + "，需要设置为FULL";
            result.addItem("Binlog Row Image", "binlog_row_image需设置为FULL", passed, message, "error");
        } catch (SQLException e) {
            result.addItem("Binlog Row Image", "binlog_row_image需设置为FULL", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private void checkServerId(Connection conn, String version, ValidationResult result) {
        try {
            String serverIdStr = getVariable(conn, "server_id");
            if (serverIdStr == null || serverIdStr.isEmpty() || "0".equals(serverIdStr)) {
                result.addItem("Server ID", "增量同步需要设置server_id", false, "server_id未设置或为0", "error");
                return;
            }
            
            long serverId = Long.parseLong(serverIdStr);
            boolean passed;
            String message;
            
            if (isVersionAtLeast(version, "5.7.0")) {
                passed = serverId >= 1 && serverId <= 4294967296L;
                message = passed ? "server_id=" + serverId + "，符合要求" : "server_id=" + serverId + "，MySQL 5.7+需要设置在1-4294967296之间";
            } else {
                passed = serverId >= 2 && serverId <= 4294967296L;
                message = passed ? "server_id=" + serverId + "，符合要求" : "server_id=" + serverId + "，MySQL 5.6及以下需要设置在2-4294967296之间";
            }
            
            result.addItem("Server ID", "增量同步需要设置server_id", passed, message, "error");
        } catch (Exception e) {
            result.addItem("Server ID", "增量同步需要设置server_id", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private void checkVersionCompatibility(String sourceVersion, String targetVersion, ValidationResult result) {
        boolean passed = compareVersions(sourceVersion, targetVersion) <= 0;
        String message = passed 
            ? "源数据库版本(" + sourceVersion + ") <= 目标数据库版本(" + targetVersion + ")" 
            : "源数据库版本(" + sourceVersion + ") > 目标数据库版本(" + targetVersion + ")，可能导致兼容性问题";
        result.addItem("版本兼容性", "源数据库版本不能高于目标数据库版本", passed, message, "error");
    }

    private void checkSqlModeCompatibility(Connection sourceDb, Connection targetDb, ValidationResult result) {
        try {
            String sourceSqlMode = getVariable(sourceDb, "sql_mode");
            String targetSqlMode = getVariable(targetDb, "sql_mode");
            
            Set<String> sourceModes = parseSqlMode(sourceSqlMode);
            Set<String> targetModes = parseSqlMode(targetSqlMode);
            
            boolean passed = sourceModes.equals(targetModes);
            String message = passed 
                ? "sql_mode一致" 
                : "源数据库sql_mode(" + sourceSqlMode + ")与目标数据库(" + targetSqlMode + ")不一致";
            result.addItem("SQL Mode一致性", "源和目标数据库sql_mode需要一致", passed, message, "warning");
        } catch (SQLException e) {
            result.addItem("SQL Mode一致性", "源和目标数据库sql_mode需要一致", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private void checkSourcePermissions(Connection conn, String migrationMode, ValidationResult result) {
        try {
            Set<String> grantedPrivileges = getGrantedPrivileges(conn);
            
            Set<String> requiredPrivileges = new HashSet<>(Arrays.asList("SELECT", "SHOW VIEW", "EVENT"));
            
            if ("fullAndIncre".equals(migrationMode) || "increment".equals(migrationMode)) {
                requiredPrivileges.addAll(Arrays.asList("LOCK TABLES", "REPLICATION SLAVE", "REPLICATION CLIENT"));
            }
            
            Set<String> missingPrivileges = new HashSet<>();
            for (String priv : requiredPrivileges) {
                boolean hasPriv = grantedPrivileges.contains(priv) || 
                                  grantedPrivileges.contains("ALL PRIVILEGES") ||
                                  grantedPrivileges.contains("ALL");
                if (!hasPriv) {
                    missingPrivileges.add(priv);
                }
            }
            
            boolean passed = missingPrivileges.isEmpty();
            String mode = "fullAndIncre".equals(migrationMode) ? "全量+增量" : "full".equals(migrationMode) ? "全量" : "增量";
            String message = passed 
                ? mode + "同步所需权限已具备" 
                : mode + "同步缺少权限: " + String.join(", ", missingPrivileges);
            result.addItem("源数据库权限", mode + "同步需要SELECT、SHOW VIEW、EVENT等权限", passed, message, "error");
        } catch (SQLException e) {
            result.addItem("源数据库权限", "检查源数据库账号权限", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private void checkTargetPermissions(Connection conn, String targetVersion, ValidationResult result) {
        try {
            Set<String> grantedPrivileges = getGrantedPrivileges(conn);
            
            Set<String> requiredPrivileges = new HashSet<>(Arrays.asList(
                "SELECT", "CREATE", "DROP", "DELETE", "INSERT", "UPDATE", "ALTER", 
                "CREATE VIEW", "CREATE ROUTINE", "REFERENCES"
            ));
            
            if (isVersionInRange(targetVersion, "8.0.14", "8.0.18")) {
                requiredPrivileges.add("SESSION_VARIABLES_ADMIN");
            }
            
            Set<String> missingPrivileges = new HashSet<>();
            for (String priv : requiredPrivileges) {
                boolean hasPriv = grantedPrivileges.contains(priv) || 
                                  grantedPrivileges.contains("ALL PRIVILEGES") ||
                                  grantedPrivileges.contains("ALL");
                if (!hasPriv) {
                    missingPrivileges.add(priv);
                }
            }
            
            boolean passed = missingPrivileges.isEmpty();
            String message = passed 
                ? "目标数据库所需权限已具备" 
                : "目标数据库缺少权限: " + String.join(", ", missingPrivileges);
            result.addItem("目标数据库权限", "目标数据库需要SELECT、CREATE、DROP、INSERT、UPDATE、ALTER等权限", passed, message, "error");
        } catch (SQLException e) {
            result.addItem("目标数据库权限", "检查目标数据库账号权限", false, "检查失败: " + e.getMessage(), "error");
        }
    }

    private String getVariable(Connection conn, String variableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE '" + variableName + "'")) {
            if (rs.next()) {
                return rs.getString("Value");
            }
        }
        return null;
    }

    private Set<String> getGrantedPrivileges(Connection conn) throws SQLException {
        Set<String> privileges = new HashSet<>();
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW GRANTS")) {
            while (rs.next()) {
                String grant = rs.getString(1);
                Set<String> extracted = parseGrants(grant);
                privileges.addAll(extracted);
            }
        }
        
        return privileges;
    }

    private Set<String> parseGrants(String grant) {
        Set<String> privileges = new HashSet<>();
        
        int start = grant.indexOf("GRANT ");
        int end = grant.indexOf(" ON ");
        if (start >= 0 && end > start) {
            String privStr = grant.substring(start + 6, end).trim();
            String[] privs = privStr.split(",");
            for (String priv : privs) {
                privileges.add(priv.trim().toUpperCase());
            }
        }
        
        return privileges;
    }

    private Set<String> parseSqlMode(String sqlMode) {
        Set<String> modes = new HashSet<>();
        if (sqlMode != null && !sqlMode.isEmpty()) {
            String[] parts = sqlMode.split(",");
            for (String part : parts) {
                modes.add(part.trim().toUpperCase());
            }
        }
        return modes;
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9].*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isVersionAtLeast(String version, String minVersion) {
        return compareVersions(version, minVersion) >= 0;
    }

    private boolean isVersionInRange(String version, String minVersion, String maxVersion) {
        return compareVersions(version, minVersion) >= 0 && compareVersions(version, maxVersion) <= 0;
    }
}

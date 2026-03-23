package com.synctask.controller;

import com.synctask.dto.ConnectionRequest;
import com.synctask.dto.DatabaseInfo;
import com.synctask.dto.TableInfo;
import com.synctask.dto.ValidationResult;
import com.synctask.service.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private static final Logger logger = LoggerFactory.getLogger(MetadataController.class);

    @Autowired
    private MetadataService metadataService;

    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(@RequestBody ConnectionRequest request) {
        try {
            logger.info("测试数据库连接: {}", maskConnection(request.getSourceConnection()));
            
            boolean success = metadataService.testConnection(request.getSourceConnection());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of("connected", success));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("测试连接失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateForMigration(@RequestBody Map<String, String> request) {
        try {
            String sourceConnection = request.get("sourceConnection");
            String targetConnection = request.get("targetConnection");
            String migrationMode = request.get("migrationMode");
            
            logger.info("校验数据库同步条件: mode={}", migrationMode);
            
            ValidationResult result = metadataService.validateForMigration(
                sourceConnection, targetConnection, migrationMode);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result
            ));
        } catch (Exception e) {
            logger.error("校验失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/databases")
    public ResponseEntity<?> listDatabases(@RequestBody ConnectionRequest request) {
        try {
            logger.info("查询数据库列表: {}", maskConnection(request.getSourceConnection()));
            
            List<String> databases = metadataService.listDatabases(request.getSourceConnection());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of("databases", databases));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询数据库列表失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/tables")
    public ResponseEntity<?> listTables(@RequestBody Map<String, String> request) {
        try {
            String connectionStr = request.get("sourceConnection");
            String database = request.get("database");
            
            logger.info("查询表列表: database={}", database);
            
            List<TableInfo> tables = metadataService.listTables(connectionStr, database);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of("database", database, "tables", tables));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询表列表失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/database-info")
    public ResponseEntity<?> getDatabaseInfo(@RequestBody Map<String, String> request) {
        try {
            String connectionStr = request.get("sourceConnection");
            String database = request.get("database");
            
            logger.info("获取数据库信息: database={}", database);
            
            DatabaseInfo dbInfo = metadataService.getDatabaseWithTables(connectionStr, database);
            
            return ResponseEntity.ok(Map.of("success", true, "data", dbInfo));
        } catch (Exception e) {
            logger.error("获取数据库信息失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    private String maskConnection(String connectionStr) {
        if (connectionStr == null) return "null";
        return connectionStr.replaceAll(":[^:@]+@", ":****@");
    }
}

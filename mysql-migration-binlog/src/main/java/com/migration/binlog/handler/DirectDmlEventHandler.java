package com.migration.binlog.handler;

import com.migration.binlog.core.BinlogEvent;
import com.migration.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * 直接执行 DML 事件处理器
 * 处理 INSERT、UPDATE、DELETE 语句，直接执行到目标数据库
 */
public class DirectDmlEventHandler implements BinlogEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(DirectDmlEventHandler.class);

    private DatabaseConnection targetConnection;

    public DirectDmlEventHandler(DatabaseConnection targetConnection) {
        this.targetConnection = targetConnection;
    }

    @Override
    public boolean handle(BinlogEvent event) {
        if (!supports(event)) {
            return false;
        }

        BinlogEvent.DmlEventData dmlData = (BinlogEvent.DmlEventData) event.getData();
        String database = event.getDatabase();
        String table = event.getTable();

        try {
            switch (dmlData.getDmlType()) {
                case INSERT:
                    return handleInsert(database, table, dmlData.getAfterRows());
                case UPDATE:
                    return handleUpdate(database, table, dmlData.getBeforeRows(), dmlData.getAfterRows());
                case DELETE:
                    return handleDelete(database, table, dmlData.getBeforeRows());
                default:
                    logger.warn("未知的 DML 类型: {}", dmlData.getDmlType());
                    return false;
            }
        } catch (SQLException e) {
            logger.error("处理 DML 事件失败: {}.{}, type={}", database, table, dmlData.getDmlType(), e);
            return false;
        }
    }

    @Override
    public boolean supports(BinlogEvent event) {
        return event != null && event.isDmlEvent();
    }

    /**
     * 处理 INSERT 操作
     */
    private boolean handleInsert(String database, String table, List<Map<String, Serializable>> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return true;
        }

        String fullTableName = database + "." + table;

        for (Map<String, Serializable> row : rows) {
            StringBuilder sql = new StringBuilder("INSERT INTO ");
            sql.append(fullTableName).append(" (");

            // 构建列名
            String[] columns = row.keySet().toArray(new String[0]);
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append(columns[i]);
            }

            sql.append(") VALUES (");

            // 构建占位符
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(", ");
                sql.append("?");
            }
            sql.append(")");

            // 执行插入
            try (PreparedStatement stmt = targetConnection.getConnection().prepareStatement(sql.toString())) {
                for (int i = 0; i < columns.length; i++) {
                    stmt.setObject(i + 1, row.get(columns[i]));
                }
                stmt.executeUpdate();
            }
        }

        logger.debug("已处理 INSERT: {}.{}, 行数: {}", database, table, rows.size());
        return true;
    }

    /**
     * 处理 UPDATE 操作
     */
    private boolean handleUpdate(String database, String table,
                                 List<Map<String, Serializable>> beforeRows,
                                 List<Map<String, Serializable>> afterRows) throws SQLException {
        if (beforeRows == null || afterRows == null || beforeRows.isEmpty()) {
            return true;
        }

        String fullTableName = database + "." + table;

        for (int i = 0; i < afterRows.size(); i++) {
            Map<String, Serializable> beforeRow = beforeRows.get(i);
            Map<String, Serializable> afterRow = afterRows.get(i);

            StringBuilder sql = new StringBuilder("UPDATE ");
            sql.append(fullTableName).append(" SET ");

            // 构建 SET 子句
            String[] columns = afterRow.keySet().toArray(new String[0]);
            for (int j = 0; j < columns.length; j++) {
                if (j > 0) sql.append(", ");
                sql.append(columns[j]).append(" = ?");
            }

            // 构建 WHERE 子句（使用主键或所有列）
            sql.append(" WHERE ");
            String[] beforeColumns = beforeRow.keySet().toArray(new String[0]);
            for (int j = 0; j < beforeColumns.length; j++) {
                if (j > 0) sql.append(" AND ");
                sql.append(beforeColumns[j]).append(" = ?");
            }

            // 执行更新
            try (PreparedStatement stmt = targetConnection.getConnection().prepareStatement(sql.toString())) {
                int paramIndex = 1;

                // SET 参数
                for (String column : columns) {
                    stmt.setObject(paramIndex++, afterRow.get(column));
                }

                // WHERE 参数
                for (String column : beforeColumns) {
                    stmt.setObject(paramIndex++, beforeRow.get(column));
                }

                stmt.executeUpdate();
            }
        }

        logger.debug("已处理 UPDATE: {}.{}, 行数: {}", database, table, afterRows.size());
        return true;
    }

    /**
     * 处理 DELETE 操作
     */
    private boolean handleDelete(String database, String table, List<Map<String, Serializable>> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) {
            return true;
        }

        String fullTableName = database + "." + table;

        for (Map<String, Serializable> row : rows) {
            StringBuilder sql = new StringBuilder("DELETE FROM ");
            sql.append(fullTableName).append(" WHERE ");

            // 构建 WHERE 子句
            String[] columns = row.keySet().toArray(new String[0]);
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(" AND ");
                sql.append(columns[i]).append(" = ?");
            }

            // 执行删除
            try (PreparedStatement stmt = targetConnection.getConnection().prepareStatement(sql.toString())) {
                for (int i = 0; i < columns.length; i++) {
                    stmt.setObject(i + 1, row.get(columns[i]));
                }
                stmt.executeUpdate();
            }
        }

        logger.debug("已处理 DELETE: {}.{}, 行数: {}", database, table, rows.size());
        return true;
    }
}
package com.migration.binlog.handler;

import com.migration.binlog.core.BinlogEvent;
import com.migration.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * DDL 事件处理器
 * 处理 CREATE、ALTER、DROP 等 DDL 语句
 */
public class DdlEventHandler implements BinlogEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(DdlEventHandler.class);

    private DatabaseConnection targetConnection;

    public DdlEventHandler(DatabaseConnection targetConnection) {
        this.targetConnection = targetConnection;
    }

    @Override
    public boolean handle(BinlogEvent event) {
        if (!supports(event)) {
            return false;
        }

        BinlogEvent.DdlEventData ddlData = (BinlogEvent.DdlEventData) event.getData();
        String sql = ddlData.getSql();

        // 过滤掉一些不需要执行的语句
        if (shouldSkipSql(sql)) {
            logger.debug("跳过 SQL: {}", sql);
            return true;
        }

        try {
            targetConnection.execute(sql);
            logger.info("已执行 DDL: {}", sql);
            return true;
        } catch (SQLException e) {
            logger.error("执行 DDL 失败: {}", sql, e);
            return false;
        }
    }

    @Override
    public boolean supports(BinlogEvent event) {
        return event != null && event.isDdlEvent();
    }

    /**
     * 判断是否应该跳过该 SQL
     */
    private boolean shouldSkipSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return true;
        }

        String upperSql = sql.toUpperCase().trim();

        // 跳过 BEGIN、COMMIT 等事务控制语句
        if (upperSql.equals("BEGIN") ||
            upperSql.equals("COMMIT") ||
            upperSql.equals("ROLLBACK")) {
            return true;
        }

        // 跳过一些系统表的 DDL
        if (upperSql.contains("MYSQL.") ||
            upperSql.contains("INFORMATION_SCHEMA.") ||
            upperSql.contains("PERFORMANCE_SCHEMA.")) {
            return true;
        }

        return false;
    }
}

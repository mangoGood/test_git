package com.migration.binlog;

import com.migration.binlog.core.BinlogPosition;
import com.migration.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Binlog 管理类
 * 负责读取和解析 MySQL binlog，实现增量数据迁移
 * <p>
 * 此类现在作为 BinlogService 的包装器，提供向后兼容的 API。
 * 建议使用新的 BinlogService 类以获得更灵活的控制。
 *
 * @deprecated 建议使用 {@link BinlogService} 替代
 */
@Deprecated
public class BinlogManager {
    private static final Logger logger = LoggerFactory.getLogger(BinlogManager.class);

    private BinlogService binlogService;
    private Set<String> includedDatabases;
    private Set<String> includedTables;

    public BinlogManager(DatabaseConnection sourceConnection,
                         DatabaseConnection targetConnection,
                         Set<String> includedDatabases,
                         Set<String> includedTables) {
        this.includedDatabases = includedDatabases;
        this.includedTables = includedTables;
        this.binlogService = new BinlogService(sourceConnection, targetConnection);
        this.binlogService.setEventFilter(includedDatabases, includedTables);
    }

    /**
     * 获取当前 binlog 位置
     */
    public BinlogPosition getCurrentBinlogPosition() throws SQLException {
        return binlogService.getCurrentBinlogPosition();
    }

    /**
     * 设置开始位置
     */
    public void setStartPosition(BinlogPosition position) {
        binlogService.setStartPosition(position);
    }

    /**
     * 启动 binlog 监听
     */
    public void start() throws IOException {
        binlogService.start();
    }

    /**
     * 停止 binlog 监听
     */
    public void stop() {
        binlogService.stop();
    }

    /**
     * 获取当前位置
     */
    public BinlogPosition getCurrentPosition() {
        return binlogService.getCurrentPosition();
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return binlogService.isRunning();
    }

    /**
     * 等待指定时间
     */
    public void waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        binlogService.waitFor(timeout, unit);
    }

    /**
     * 获取底层的 BinlogService（用于高级用法）
     */
    public BinlogService getBinlogService() {
        return binlogService;
    }
}

package com.migration.binlog;

import com.migration.binlog.client.BinlogClient;
import com.migration.binlog.core.BinlogEvent;
import com.migration.binlog.core.BinlogPosition;
import com.migration.binlog.handler.BinlogEventHandler;
import com.migration.binlog.handler.DdlEventHandler;
import com.migration.binlog.handler.DirectDmlEventHandler;
import com.migration.binlog.handler.DmlEventHandler;
import com.migration.binlog.listener.BinlogEventFilter;
import com.migration.binlog.listener.BinlogEventListener;
import com.migration.config.DatabaseConfig;
import com.migration.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Binlog 服务类
 * 提供高层次的 binlog 监听和管理功能
 * 整合了客户端、处理器和监听器
 */
public class BinlogService {
    private static final Logger logger = LoggerFactory.getLogger(BinlogService.class);

    private BinlogClient binlogClient;
    private DatabaseConnection sourceConnection;
    private DatabaseConnection targetConnection;
    private String sqlOutputDirectory;
    private List<BinlogEventHandler> handlers = new CopyOnWriteArrayList<>();
    private List<BinlogEventListener> listeners = new CopyOnWriteArrayList<>();
    private DmlEventHandler dmlEventHandler;

    public BinlogService(DatabaseConnection sourceConnection, DatabaseConnection targetConnection) {
        this(sourceConnection, targetConnection, null);
    }

    public BinlogService(DatabaseConnection sourceConnection, DatabaseConnection targetConnection, String sqlOutputDirectory) {
        this.sourceConnection = sourceConnection;
        this.targetConnection = targetConnection;
        this.sqlOutputDirectory = sqlOutputDirectory;

        DatabaseConfig config = sourceConnection.getConfig();
        this.binlogClient = new BinlogClient(
                config.getHost(),
                config.getPort(),
                config.getUsername(),
                config.getPassword(),
                sourceConnection
        );

        // 注册默认的事件处理器
        registerDefaultHandlers();

        // 注册事件分发监听器
        binlogClient.addEventListener(new EventDispatcher());
    }

    /**
     * 注册默认的事件处理器
     */
    private void registerDefaultHandlers() {
        if (sqlOutputDirectory != null) {
            // 如果指定了输出目录，将 SQL 写入文件
            dmlEventHandler = new DmlEventHandler(sqlOutputDirectory);
            handlers.add(dmlEventHandler);
            logger.info("DML 事件将写入目录: {}", sqlOutputDirectory);
        } else if (targetConnection != null) {
            // 否则直接执行到目标数据库
            handlers.add(new DdlEventHandler(targetConnection));
            handlers.add(new DirectDmlEventHandler(targetConnection));
        }
    }

    /**
     * 获取 DML 事件处理器
     */
    public DmlEventHandler getDmlEventHandler() {
        return dmlEventHandler;
    }

    /**
     * 获取当前 binlog 位置
     */
    public BinlogPosition getCurrentBinlogPosition() throws SQLException {
        String sql = "SHOW MASTER STATUS";

        try (var stmt = sourceConnection.getConnection().createStatement();
             var rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                String filename = rs.getString("File");
                long position = rs.getLong("Position");
                BinlogPosition binlogPosition = new BinlogPosition(filename, position);
                logger.info("当前 binlog 位置: {}", binlogPosition);
                return binlogPosition;
            }
        }

        throw new SQLException("无法获取当前 binlog 位置");
    }

    /**
     * 设置开始位置
     */
    public void setStartPosition(BinlogPosition position) {
        binlogClient.setStartPosition(position);
        logger.info("设置 binlog 开始位置: {}", position);
    }

    /**
     * 设置事件过滤器
     */
    public void setEventFilter(Set<String> includedDatabases, Set<String> includedTables) {
        BinlogEventFilter filter = new BinlogEventFilter(includedDatabases, includedTables);
        binlogClient.setEventFilter(filter);
    }

    /**
     * 添加事件处理器
     */
    public void addHandler(BinlogEventHandler handler) {
        handlers.add(handler);
    }

    /**
     * 移除事件处理器
     */
    public void removeHandler(BinlogEventHandler handler) {
        handlers.remove(handler);
    }

    /**
     * 添加事件监听器
     */
    public void addEventListener(BinlogEventListener listener) {
        listeners.add(listener);
        binlogClient.addEventListener(listener);
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(BinlogEventListener listener) {
        listeners.remove(listener);
        binlogClient.removeEventListener(listener);
    }

    /**
     * 启动 binlog 监听
     */
    public void start() throws IOException {
        binlogClient.start();
    }

    /**
     * 停止 binlog 监听
     */
    public void stop() {
        binlogClient.stop();
    }

    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return binlogClient.isRunning();
    }

    /**
     * 获取当前位置
     */
    public BinlogPosition getCurrentPosition() {
        return binlogClient.getCurrentPosition();
    }

    /**
     * 等待指定时间
     */
    public void waitFor(long timeout, TimeUnit unit) throws InterruptedException {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        while (isRunning() && System.currentTimeMillis() < endTime) {
            Thread.sleep(100);
        }
    }

    /**
     * 事件分发器内部类
     */
    private class EventDispatcher implements BinlogEventListener {
        @Override
        public void onEvent(BinlogEvent event) {
            // 分发到所有处理器
            for (BinlogEventHandler handler : handlers) {
                if (handler.supports(event)) {
                    try {
                        boolean success = handler.handle(event);
                        if (!success) {
                            logger.warn("事件处理失败: {}", event);
                        }
                    } catch (Exception e) {
                        logger.error("处理事件时发生异常: {}", event, e);
                    }
                }
            }
        }

        @Override
        public void onError(Throwable error) {
            logger.error("Binlog 监听发生错误", error);
        }

        @Override
        public void onConnect() {
            logger.info("Binlog 服务已连接");
        }

        @Override
        public void onDisconnect() {
            logger.info("Binlog 服务已断开");
        }
    }
}

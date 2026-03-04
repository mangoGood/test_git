package com.migration.binlog.client;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.migration.binlog.core.BinlogEvent;
import com.migration.binlog.core.BinlogPosition;
import com.migration.binlog.listener.BinlogEventFilter;
import com.migration.binlog.listener.BinlogEventListener;
import com.migration.db.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Binlog 客户端
 * 封装 MySQL binlog 连接和事件处理
 */
public class BinlogClient {
    private static final Logger logger = LoggerFactory.getLogger(BinlogClient.class);

    private String host;
    private int port;
    private String username;
    private String password;
    private DatabaseConnection sourceConnection;
    private BinaryLogClient binaryLogClient;
    private volatile boolean running = false;
    private BinlogPosition currentPosition;
    private BinlogPosition startPosition;
    private String currentGtid;

    private List<BinlogEventListener> listeners = new CopyOnWriteArrayList<>();
    private BinlogEventFilter eventFilter;

    // 表映射缓存：tableId -> TableMapInfo
    private Map<Long, TableMapInfo> tableMapCache = new HashMap<>();

    public BinlogClient(String host, int port, String username, String password, DatabaseConnection sourceConnection) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.sourceConnection = sourceConnection;
        this.eventFilter = new BinlogEventFilter();
    }

    /**
     * 添加事件监听器
     */
    public void addEventListener(BinlogEventListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(BinlogEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * 设置事件过滤器
     */
    public void setEventFilter(BinlogEventFilter filter) {
        this.eventFilter = filter != null ? filter : new BinlogEventFilter();
    }

    /**
     * 设置开始位置
     */
    public void setStartPosition(BinlogPosition position) {
        this.startPosition = position;
    }

    /**
     * 连接到 binlog 并开始监听
     */
    public void start() throws IOException {
        if (running) {
            logger.warn("Binlog 客户端已在运行中");
            return;
        }

        binaryLogClient = new BinaryLogClient(host, port, username, password);

        // 设置唯一的 server_id，避免冲突
        long serverId = generateUniqueServerId();
        binaryLogClient.setServerId(serverId);
        logger.info("设置 Binlog 客户端 server_id: {}", serverId);

        // 设置开始位置
        if (startPosition != null) {
            binaryLogClient.setBinlogFilename(startPosition.getFilename());
            binaryLogClient.setBinlogPosition(startPosition.getPosition());
            logger.info("从指定位置开始监听: {}", startPosition);
        }

        // 注册事件监听器
        binaryLogClient.registerEventListener(this::handleEvent);

        // 注册生命周期监听器
        binaryLogClient.registerLifecycleListener(new BinaryLogClient.LifecycleListener() {
            @Override
            public void onConnect(BinaryLogClient client) {
                logger.info("已连接到 MySQL binlog: {}:{}", host, port);
                notifyConnect();
            }

            @Override
            public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                logger.error("Binlog 通信失败", ex);
                notifyError(ex);
                running = false;
            }

            @Override
            public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
                logger.error("Binlog 事件反序列化失败", ex);
                notifyError(ex);
            }

            @Override
            public void onDisconnect(BinaryLogClient client) {
                logger.info("已断开与 MySQL binlog 的连接");
                notifyDisconnect();
                running = false;
            }
        });

        running = true;
        binaryLogClient.connect();
    }

    /**
     * 生成唯一的 server_id
     * 使用时间戳和随机数的组合，确保唯一性
     */
    private long generateUniqueServerId() {
        // 使用当前时间戳的后20位（约100万秒内的唯一性）
        long timestamp = System.currentTimeMillis() & 0xFFFFF;
        // 使用随机数的后12位（0-4095）
        long random = (long) (Math.random() * 4096);
        // 组合成一个唯一的 server_id（32位以内）
        return timestamp | (random << 20);
    }

    /**
     * 停止监听
     */
    public void stop() {
        if (!running) {
            return;
        }

        try {
            if (binaryLogClient != null) {
                binaryLogClient.disconnect();
            }
        } catch (IOException e) {
            logger.error("断开 binlog 连接失败", e);
        }

        running = false;
        logger.info("Binlog 客户端已停止");
    }

    /**
     * 处理 binlog 事件
     */
    private void handleEvent(Event event) {
        if (!running) {
            return;
        }

        EventHeaderV4 header = (EventHeaderV4) event.getHeader();
        EventType eventType = header.getEventType();

        // 更新当前位置
        currentPosition = new BinlogPosition(
                binaryLogClient.getBinlogFilename(),
                header.getPosition()
        );

        // 处理 GTID 事件
        if (eventType == EventType.GTID) {
            handleGtidEvent((GtidEventData) event.getData());
            return;
        }

        // 处理表映射事件
        if (eventType == EventType.TABLE_MAP) {
            handleTableMapEvent((TableMapEventData) event.getData());
            return;
        }

        // 转换并处理事件
        BinlogEvent binlogEvent = convertEvent(event);
        if (binlogEvent == null) {
            return;
        }

        // 设置 GTID 到 position
        if (currentGtid != null && currentPosition != null) {
            currentPosition.setGtid(currentGtid);
        }

        // 应用过滤器
        if (eventFilter != null && !eventFilter.shouldProcess(binlogEvent)) {
            return;
        }

        // 通知监听器
        notifyEvent(binlogEvent);
    }

    /**
     * 处理 GTID 事件
     */
    private void handleGtidEvent(GtidEventData data) {
        currentGtid = data.getGtid();
        logger.debug("收到 GTID: {}", currentGtid);
    }

    /**
     * 处理表映射事件
     */
    private void handleTableMapEvent(TableMapEventData data) {
        // 从数据库获取实际列名
        List<String> columnNames = getColumnNames(data.getDatabase(), data.getTable());
        
        TableMapInfo info = new TableMapInfo(
                data.getTableId(),
                data.getDatabase(),
                data.getTable(),
                columnNames
        );
        tableMapCache.put(data.getTableId(), info);
    }
    
    /**
     * 从数据库获取表的列名
     */
    private List<String> getColumnNames(String database, String table) {
        List<String> columnNames = new ArrayList<>();
        
        if (sourceConnection != null) {
            String sql = "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
            
            try (var stmt = sourceConnection.getConnection().prepareStatement(sql)) {
                stmt.setString(1, database);
                stmt.setString(2, table);
                
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        columnNames.add(rs.getString("COLUMN_NAME"));
                    }
                }
            } catch (Exception e) {
                logger.warn("获取表 {} 的列名失败: {}", database + "." + table, e.getMessage());
                // 降级处理：使用默认列名
                columnNames.clear();
            }
        }
        
        // 如果没有获取到列名，使用默认列名
        if (columnNames.isEmpty()) {
            logger.warn("使用默认列名 for table: {}.{}", database, table);
        }
        
        return columnNames;
    }

    /**
     * 将原始事件转换为统一的事件对象
     */
    private BinlogEvent convertEvent(Event event) {
        EventHeaderV4 header = (EventHeaderV4) event.getHeader();
        EventType eventType = header.getEventType();

        BinlogEvent binlogEvent = new BinlogEvent(
                eventType,
                currentPosition,
                header.getTimestamp()
        );

        switch (eventType) {
            case QUERY:
                return convertQueryEvent(binlogEvent, (QueryEventData) event.getData());
            case EXT_WRITE_ROWS:
            case WRITE_ROWS:
                return convertWriteRowsEvent(binlogEvent, (WriteRowsEventData) event.getData());
            case EXT_UPDATE_ROWS:
            case UPDATE_ROWS:
                return convertUpdateRowsEvent(binlogEvent, (UpdateRowsEventData) event.getData());
            case EXT_DELETE_ROWS:
            case DELETE_ROWS:
                return convertDeleteRowsEvent(binlogEvent, (DeleteRowsEventData) event.getData());
            default:
                return null;
        }
    }

    /**
     * 转换查询事件（DDL）
     */
    private BinlogEvent convertQueryEvent(BinlogEvent binlogEvent, QueryEventData data) {
        binlogEvent.setDatabase(data.getDatabase());
        binlogEvent.setData(new BinlogEvent.DdlEventData(data.getSql()));
        return binlogEvent;
    }

    /**
     * 转换插入行事件
     */
    private BinlogEvent convertWriteRowsEvent(BinlogEvent binlogEvent, WriteRowsEventData data) {
        TableMapInfo tableInfo = tableMapCache.get(data.getTableId());
        if (tableInfo == null) {
            return null;
        }

        binlogEvent.setDatabase(tableInfo.database);
        binlogEvent.setTable(tableInfo.table);

        BinlogEvent.DmlEventData dmlData = new BinlogEvent.DmlEventData(
                BinlogEvent.DmlType.INSERT,
                data.getTableId()
        );

        List<Map<String, Serializable>> rows = new ArrayList<>();
        for (Serializable[] row : data.getRows()) {
            rows.add(convertRowToMap(row, data.getTableId()));
        }
        dmlData.setAfterRows(rows);

        binlogEvent.setData(dmlData);
        return binlogEvent;
    }

    /**
     * 转换更新行事件
     */
    private BinlogEvent convertUpdateRowsEvent(BinlogEvent binlogEvent, UpdateRowsEventData data) {
        TableMapInfo tableInfo = tableMapCache.get(data.getTableId());
        if (tableInfo == null) {
            return null;
        }

        binlogEvent.setDatabase(tableInfo.database);
        binlogEvent.setTable(tableInfo.table);

        BinlogEvent.DmlEventData dmlData = new BinlogEvent.DmlEventData(
                BinlogEvent.DmlType.UPDATE,
                data.getTableId()
        );

        List<Map<String, Serializable>> beforeRows = new ArrayList<>();
        List<Map<String, Serializable>> afterRows = new ArrayList<>();

        for (Map.Entry<Serializable[], Serializable[]> entry : data.getRows()) {
            beforeRows.add(convertRowToMap(entry.getKey(), data.getTableId()));
            afterRows.add(convertRowToMap(entry.getValue(), data.getTableId()));
        }

        dmlData.setBeforeRows(beforeRows);
        dmlData.setAfterRows(afterRows);

        binlogEvent.setData(dmlData);
        return binlogEvent;
    }

    /**
     * 转换删除行事件
     */
    private BinlogEvent convertDeleteRowsEvent(BinlogEvent binlogEvent, DeleteRowsEventData data) {
        TableMapInfo tableInfo = tableMapCache.get(data.getTableId());
        if (tableInfo == null) {
            return null;
        }

        binlogEvent.setDatabase(tableInfo.database);
        binlogEvent.setTable(tableInfo.table);

        BinlogEvent.DmlEventData dmlData = new BinlogEvent.DmlEventData(
                BinlogEvent.DmlType.DELETE,
                data.getTableId()
        );

        List<Map<String, Serializable>> rows = new ArrayList<>();
        for (Serializable[] row : data.getRows()) {
            rows.add(convertRowToMap(row, data.getTableId()));
        }
        dmlData.setBeforeRows(rows);

        binlogEvent.setData(dmlData);
        return binlogEvent;
    }

    /**
     * 将行数据转换为 Map
     */
    private Map<String, Serializable> convertRowToMap(Serializable[] row, long tableId) {
        Map<String, Serializable> map = new LinkedHashMap<>();
        TableMapInfo tableInfo = tableMapCache.get(tableId);
        
        if (tableInfo != null && tableInfo.columnNames != null && !tableInfo.columnNames.isEmpty()) {
            for (int i = 0; i < row.length; i++) {
                if (i < tableInfo.columnNames.size()) {
                    map.put(tableInfo.columnNames.get(i), row[i]);
                } else {
                    map.put("column_" + i, row[i]);
                }
            }
        } else {
            // 降级处理：如果没有表信息或列名，使用默认列名
            for (int i = 0; i < row.length; i++) {
                map.put("column_" + i, row[i]);
            }
        }
        return map;
    }

    /**
     * 通知所有监听器 - 事件
     */
    private void notifyEvent(BinlogEvent event) {
        for (BinlogEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                logger.error("通知监听器失败", e);
            }
        }
    }

    /**
     * 通知所有监听器 - 连接
     */
    private void notifyConnect() {
        for (BinlogEventListener listener : listeners) {
            try {
                listener.onConnect();
            } catch (Exception e) {
                logger.error("通知监听器连接事件失败", e);
            }
        }
    }

    /**
     * 通知所有监听器 - 断开连接
     */
    private void notifyDisconnect() {
        for (BinlogEventListener listener : listeners) {
            try {
                listener.onDisconnect();
            } catch (Exception e) {
                logger.error("通知监听器断开连接事件失败", e);
            }
        }
    }

    /**
     * 通知所有监听器 - 错误
     */
    private void notifyError(Throwable error) {
        for (BinlogEventListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                logger.error("通知监听器错误事件失败", e);
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public BinlogPosition getCurrentPosition() {
        return currentPosition;
    }

    /**
     * 表映射信息内部类
     */
    private static class TableMapInfo {
        final long tableId;
        final String database;
        final String table;
        final List<String> columnNames;

        TableMapInfo(long tableId, String database, String table, List<String> columnNames) {
            this.tableId = tableId;
            this.database = database;
            this.table = table;
            this.columnNames = columnNames;
        }
    }
}

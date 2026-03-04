package com.migration.binlog.core;

import com.github.shyiko.mysql.binlog.event.EventType;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Binlog 事件封装类
 * 统一封装各种 MySQL binlog 事件
 */
public class BinlogEvent {

    private EventType eventType;
    private BinlogPosition position;
    private long timestamp;
    private String database;
    private String table;
    private EventData data;

    public BinlogEvent(EventType eventType, BinlogPosition position, long timestamp) {
        this.eventType = eventType;
        this.position = position;
        this.timestamp = timestamp;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public BinlogPosition getPosition() {
        return position;
    }

    public void setPosition(BinlogPosition position) {
        this.position = position;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public EventData getData() {
        return data;
    }

    public void setData(EventData data) {
        this.data = data;
    }

    /**
     * 是否为 DDL 事件
     */
    public boolean isDdlEvent() {
        return eventType == EventType.QUERY;
    }

    /**
     * 是否为 DML 事件（INSERT/UPDATE/DELETE）
     */
    public boolean isDmlEvent() {
        return eventType == EventType.EXT_WRITE_ROWS ||
               eventType == EventType.EXT_UPDATE_ROWS ||
               eventType == EventType.EXT_DELETE_ROWS ||
               eventType == EventType.WRITE_ROWS ||
               eventType == EventType.UPDATE_ROWS ||
               eventType == EventType.DELETE_ROWS;
    }

    @Override
    public String toString() {
        return String.format("BinlogEvent{type=%s, database='%s', table='%s', position=%s}",
                eventType, database, table, position);
    }

    /**
     * 事件数据接口
     */
    public interface EventData {
    }

    /**
     * DDL 事件数据
     */
    public static class DdlEventData implements EventData {
        private String sql;

        public DdlEventData(String sql) {
            this.sql = sql;
        }

        public String getSql() {
            return sql;
        }
    }

    /**
     * DML 事件数据（INSERT/UPDATE/DELETE）
     */
    public static class DmlEventData implements EventData {
        private DmlType dmlType;
        private long tableId;
        private List<Map<String, Serializable>> beforeRows;
        private List<Map<String, Serializable>> afterRows;

        public DmlEventData(DmlType dmlType, long tableId) {
            this.dmlType = dmlType;
            this.tableId = tableId;
        }

        public DmlType getDmlType() {
            return dmlType;
        }

        public long getTableId() {
            return tableId;
        }

        public List<Map<String, Serializable>> getBeforeRows() {
            return beforeRows;
        }

        public void setBeforeRows(List<Map<String, Serializable>> beforeRows) {
            this.beforeRows = beforeRows;
        }

        public List<Map<String, Serializable>> getAfterRows() {
            return afterRows;
        }

        public void setAfterRows(List<Map<String, Serializable>> afterRows) {
            this.afterRows = afterRows;
        }
    }

    /**
     * DML 操作类型
     */
    public enum DmlType {
        INSERT,
        UPDATE,
        DELETE
    }
}

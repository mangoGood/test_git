package com.migration.binlog.listener;

import com.migration.binlog.core.BinlogEvent;

import java.util.Set;

/**
 * Binlog 事件过滤器
 * 用于过滤不需要处理的事件
 */
public class BinlogEventFilter {

    private Set<String> includedDatabases;
    private Set<String> includedTables;
    private boolean includeAllDatabases = false;
    private boolean includeAllTables = false;

    public BinlogEventFilter() {
    }

    public BinlogEventFilter(Set<String> includedDatabases, Set<String> includedTables) {
        this.includedDatabases = includedDatabases;
        this.includedTables = includedTables;
        this.includeAllDatabases = (includedDatabases == null || includedDatabases.isEmpty());
        this.includeAllTables = (includedTables == null || includedTables.isEmpty());
    }

    /**
     * 判断是否应该处理该事件
     */
    public boolean shouldProcess(BinlogEvent event) {
        if (event == null) {
            return false;
        }

        String database = event.getDatabase();
        String table = event.getTable();

        return shouldProcessDatabase(database) && shouldProcessTable(database, table);
    }

    /**
     * 判断是否应该处理该数据库
     */
    public boolean shouldProcessDatabase(String database) {
        if (database == null || database.isEmpty()) {
            return false;
        }

        if (includeAllDatabases) {
            return true;
        }

        return includedDatabases.contains(database);
    }

    /**
     * 判断是否应该处理该表
     */
    public boolean shouldProcessTable(String database, String table) {
        if (table == null || table.isEmpty()) {
            return false;
        }

        if (!shouldProcessDatabase(database)) {
            return false;
        }

        if (includeAllTables) {
            return true;
        }

        String fullTableName = database + "." + table;
        return includedTables.contains(fullTableName) || includedTables.contains(table);
    }

    public Set<String> getIncludedDatabases() {
        return includedDatabases;
    }

    public void setIncludedDatabases(Set<String> includedDatabases) {
        this.includedDatabases = includedDatabases;
        this.includeAllDatabases = (includedDatabases == null || includedDatabases.isEmpty());
    }

    public Set<String> getIncludedTables() {
        return includedTables;
    }

    public void setIncludedTables(Set<String> includedTables) {
        this.includedTables = includedTables;
        this.includeAllTables = (includedTables == null || includedTables.isEmpty());
    }
}

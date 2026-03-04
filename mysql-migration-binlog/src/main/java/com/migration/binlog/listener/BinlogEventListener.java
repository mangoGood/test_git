package com.migration.binlog.listener;

import com.migration.binlog.core.BinlogEvent;

/**
 * Binlog 事件监听器接口
 */
public interface BinlogEventListener {

    /**
     * 当接收到 binlog 事件时调用
     *
     * @param event binlog 事件
     */
    void onEvent(BinlogEvent event);

    /**
     * 当发生异常时调用
     *
     * @param error 异常信息
     */
    default void onError(Throwable error) {
        // 默认实现，子类可以覆盖
    }

    /**
     * 当连接建立时调用
     */
    default void onConnect() {
        // 默认实现，子类可以覆盖
    }

    /**
     * 当连接断开时调用
     */
    default void onDisconnect() {
        // 默认实现，子类可以覆盖
    }
}

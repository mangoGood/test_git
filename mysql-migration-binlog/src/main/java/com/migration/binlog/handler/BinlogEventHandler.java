package com.migration.binlog.handler;

import com.migration.binlog.core.BinlogEvent;

/**
 * Binlog 事件处理器接口
 * 用于处理具体的 binlog 事件
 */
public interface BinlogEventHandler {

    /**
     * 处理 binlog 事件
     *
     * @param event binlog 事件
     * @return 是否成功处理
     */
    boolean handle(BinlogEvent event);

    /**
     * 是否支持处理该类型的事件
     *
     * @param event 事件
     * @return 是否支持
     */
    boolean supports(BinlogEvent event);
}

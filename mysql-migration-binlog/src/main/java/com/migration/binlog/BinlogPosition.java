package com.migration.binlog;

/**
 * Binlog 位置信息类
 * 此类已移动到 com.migration.binlog.core 包。
 * 保留此类以向后兼容，建议使用新的位置。
 *
 * @deprecated 建议使用 {@link com.migration.binlog.core.BinlogPosition} 替代
 */
@Deprecated
public class BinlogPosition extends com.migration.binlog.core.BinlogPosition {
    private static final long serialVersionUID = 1L;

    public BinlogPosition() {
        super();
    }

    public BinlogPosition(String filename, long position) {
        super(filename, position);
    }
}

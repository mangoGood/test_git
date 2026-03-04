package com.migration.binlog.core;

import java.io.Serializable;
import java.util.Objects;

/**
 * Binlog 位置信息类
 * 用于记录 MySQL binlog 的位置，以便实现增量迁移
 */
public class BinlogPosition implements Serializable, Comparable<BinlogPosition> {
    private static final long serialVersionUID = 1L;

    private String filename;
    private long position;
    private long timestamp;
    private String gtid;

    public BinlogPosition() {
        this.timestamp = System.currentTimeMillis();
    }

    public BinlogPosition(String filename, long position) {
        this.filename = filename;
        this.position = position;
        this.timestamp = System.currentTimeMillis();
    }

    public BinlogPosition(String filename, long position, String gtid) {
        this.filename = filename;
        this.position = position;
        this.gtid = gtid;
        this.timestamp = System.currentTimeMillis();
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getGtid() {
        return gtid;
    }

    public void setGtid(String gtid) {
        this.gtid = gtid;
    }

    /**
     * 将位置信息转换为字符串格式
     */
    public String toPositionString() {
        return filename + ":" + position;
    }

    /**
     * 从字符串解析位置信息
     */
    public static BinlogPosition fromString(String positionStr) {
        if (positionStr == null || positionStr.isEmpty()) {
            return null;
        }

        String[] parts = positionStr.split(":");
        if (parts.length != 2) {
            return null;
        }

        try {
            String filename = parts[0];
            long position = Long.parseLong(parts[1]);
            return new BinlogPosition(filename, position);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        if (gtid != null && !gtid.isEmpty()) {
            return String.format("BinlogPosition{filename='%s', position=%d, gtid='%s', timestamp=%d}",
                    filename, position, gtid, timestamp);
        }
        return String.format("BinlogPosition{filename='%s', position=%d, timestamp=%d}",
                filename, position, timestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BinlogPosition that = (BinlogPosition) obj;
        return position == that.position &&
                Objects.equals(filename, that.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filename, position);
    }

    @Override
    public int compareTo(BinlogPosition other) {
        if (other == null) {
            return 1;
        }

        // 先比较文件名
        int filenameCompare = this.filename.compareTo(other.filename);
        if (filenameCompare != 0) {
            return filenameCompare;
        }

        // 再比较位置
        return Long.compare(this.position, other.position);
    }
}

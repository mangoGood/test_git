-- Binlog SQL Export
-- Generated at: 2026-03-04T12:30:30.298731
-- File: binlog_sql_20260304_123030_0001.sql
-- Format: [POSITION] filename:position, [GTID] gtid_value, SQL statement

[POSITION] binlog.000012:10171
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:32
UPDATE myapp_db.test1 SET id = 15, name = 'jack1311211' WHERE id = 15 AND name = 'jack13112121';

[POSITION] binlog.000012:10501
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:33
UPDATE myapp_db.test1 SET id = 15, name = 'jack13112121' WHERE id = 15 AND name = 'jack1311211';

[POSITION] binlog.000012:10831
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:34
UPDATE myapp_db.test1 SET id = 15, name = 'jack11' WHERE id = 15 AND name = 'jack13112121';


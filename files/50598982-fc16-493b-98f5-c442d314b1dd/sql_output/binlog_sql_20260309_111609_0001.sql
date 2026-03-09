-- Binlog SQL Export
-- Generated at: 2026-03-09T11:16:09.101709
-- File: binlog_sql_20260309_111609_0001.sql
-- Format: [POSITION] filename:position, [GTID] gtid_value, SQL statement

[POSITION] binlog.000012:272643054
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:380
UPDATE myapp_db.test1 SET id = 15, name = 'jack122' WHERE id = 15 AND name = 'jack1222';

[POSITION] binlog.000012:272643376
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:381
UPDATE myapp_db.test1 SET id = 15, name = 'jack12' WHERE id = 15 AND name = 'jack122';


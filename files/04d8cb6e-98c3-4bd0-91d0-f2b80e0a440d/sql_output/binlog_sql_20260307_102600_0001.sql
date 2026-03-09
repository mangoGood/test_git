-- Binlog SQL Export
-- Generated at: 2026-03-07T10:26:00.666538
-- File: binlog_sql_20260307_102600_0001.sql
-- Format: [POSITION] filename:position, [GTID] gtid_value, SQL statement

[POSITION] binlog.000012:272640474
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:372
UPDATE myapp_db.test1 SET id = 15, name = 'jack1x12' WHERE id = 15 AND name = 'jack1112';

[POSITION] binlog.000012:272640797
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:373
UPDATE myapp_db.test1 SET id = 15, name = 'jack21x12' WHERE id = 15 AND name = 'jack1x12';


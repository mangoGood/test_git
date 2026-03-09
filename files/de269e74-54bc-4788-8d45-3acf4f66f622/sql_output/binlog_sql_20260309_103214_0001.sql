-- Binlog SQL Export
-- Generated at: 2026-03-09T10:32:14.106227
-- File: binlog_sql_20260309_103214_0001.sql
-- Format: [POSITION] filename:position, [GTID] gtid_value, SQL statement

[POSITION] binlog.000012:272642092
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:377
UPDATE myapp_db.test1 SET id = 15, name = 'jack12' WHERE id = 15 AND name = 'jack1x2';

[POSITION] binlog.000012:272642412
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:378
UPDATE myapp_db.test1 SET id = 15, name = 'jack122' WHERE id = 15 AND name = 'jack12';


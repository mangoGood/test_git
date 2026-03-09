-- Binlog SQL Export
-- Generated at: 2026-03-09T11:09:36.972572
-- File: binlog_sql_20260309_110936_0001.sql
-- Format: [POSITION] filename:position, [GTID] gtid_value, SQL statement

[POSITION] binlog.000012:272642732
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:379
UPDATE myapp_db.test1 SET id = 15, name = 'jack1222' WHERE id = 15 AND name = 'jack122';

[POSITION] binlog.000012:272643054
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:380
UPDATE myapp_db.test1 SET id = 15, name = 'jack122' WHERE id = 15 AND name = 'jack1222';

[POSITION] binlog.000012:272643376
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:381
UPDATE myapp_db.test1 SET id = 15, name = 'jack12' WHERE id = 15 AND name = 'jack122';

[POSITION] binlog.000012:272643696
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:382
UPDATE myapp_db.test1 SET id = 15, name = 'jack122' WHERE id = 15 AND name = 'jack12';

[POSITION] binlog.000012:272644016
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:383
UPDATE myapp_db.test1 SET id = 15, name = 'jack1212' WHERE id = 15 AND name = 'jack122';

[POSITION] binlog.000012:272644338
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:384
UPDATE myapp_db.test1 SET id = 15, name = 'jack122' WHERE id = 15 AND name = 'jack1212';


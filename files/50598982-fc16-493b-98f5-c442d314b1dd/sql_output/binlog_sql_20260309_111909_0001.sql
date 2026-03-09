-- Binlog SQL Export
-- Generated at: 2026-03-09T11:19:09.902535
-- File: binlog_sql_20260309_111909_0001.sql
-- Format: [POSITION] filename:position, [GTID] gtid_value, SQL statement

[POSITION] binlog.000012:272644016
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:383
UPDATE myapp_db.test1 SET id = 15, name = 'jack1212' WHERE id = 15 AND name = 'jack122';

[POSITION] binlog.000012:272644338
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:384
UPDATE myapp_db.test1 SET id = 15, name = 'jack122' WHERE id = 15 AND name = 'jack1212';

[POSITION] binlog.000012:272644660
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:385
UPDATE myapp_db.test1 SET id = 15, name = 'jack22' WHERE id = 15 AND name = 'jack122';

[POSITION] binlog.000012:272644971
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:386
INSERT INTO myapp_db.test1 (id, name) VALUES (16, 'aaabc');

[POSITION] binlog.000012:272645276
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:387
UPDATE myapp_db.test1 SET id = 15, name = 'jack222' WHERE id = 15 AND name = 'jack22';

[POSITION] binlog.000012:272645587
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:388
INSERT INTO myapp_db.test1 (id, name) VALUES (126, 'aaabc');

[POSITION] binlog.000012:272645892
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:389
UPDATE myapp_db.test1 SET id = 126, name = 'jack' WHERE id = 126 AND name = 'aaabc';

[POSITION] binlog.000012:272646208
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:390
UPDATE myapp_db.test1 SET id = 15, name = 'jack' WHERE id = 15 AND name = 'jack222';

[POSITION] binlog.000012:272646526
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:391
UPDATE myapp_db.test1 SET id = 15, name = 'jack1' WHERE id = 15 AND name = 'jack';

[POSITION] binlog.000012:272646842
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:392
UPDATE myapp_db.test1 SET id = 15, name = 'jack12' WHERE id = 15 AND name = 'jack1';

[POSITION] binlog.000012:272647160
[GTID] a890051c-eb78-11f0-a60f-dafc9531d26a:393
UPDATE myapp_db.test1 SET id = 15, name = 'jack121' WHERE id = 15 AND name = 'jack12';


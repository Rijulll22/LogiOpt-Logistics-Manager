-- V2: make sure route_legs has the unique key (route_id, sequence_no) that V3's upsert relies on.
-- MySQL has no "ADD UNIQUE KEY IF NOT EXISTS", so the ALTER is built only when the key is missing.
-- If duplicate (route_id, sequence_no) rows exist the ALTER fails and the runner stops: remove the
-- duplicates by hand, delete the failed V2 row from schema_migrations, then run again.

SET @logiopt_uk_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'route_legs'
                            AND INDEX_NAME = 'uk_route_legs_seq');
SET @logiopt_uk_sql = IF(@logiopt_uk_exists = 0,
                         'ALTER TABLE route_legs ADD UNIQUE KEY uk_route_legs_seq (route_id, sequence_no)',
                         'DO 0');
PREPARE logiopt_uk_stmt FROM @logiopt_uk_sql;
EXECUTE logiopt_uk_stmt;
DEALLOCATE PREPARE logiopt_uk_stmt;
SET @logiopt_uk_exists = NULL;
SET @logiopt_uk_sql = NULL;

-- V8: natural key for routes, so no corridor can hold two routes with the same name and V9's
-- generated rows can be matched by (source, destination, name) as well as by id.
-- MySQL has no "ADD UNIQUE KEY IF NOT EXISTS", so the ALTER is built only when the key is missing.
-- If two routes of one corridor share a name the ALTER fails and the runner stops: rename one by
-- hand, delete the failed V8 row from schema_migrations, then run again. No row is changed.

SET @logiopt_uk_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
                          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'routes'
                            AND INDEX_NAME = 'uk_routes_pair_name');
SET @logiopt_uk_sql = IF(@logiopt_uk_exists = 0,
                         'ALTER TABLE routes ADD UNIQUE KEY uk_routes_pair_name (source, destination, name)',
                         'DO 0');
PREPARE logiopt_uk_stmt FROM @logiopt_uk_sql;
EXECUTE logiopt_uk_stmt;
DEALLOCATE PREPARE logiopt_uk_stmt;
SET @logiopt_uk_exists = NULL;
SET @logiopt_uk_sql = NULL;

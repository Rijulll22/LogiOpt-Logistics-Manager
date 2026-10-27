-- Test-only migration: the first statement succeeds (and, being DDL, commits implicitly);
-- the second fails, so the runner must record V4 as failed and refuse later runs.
CREATE TABLE migration_partial_marker (id INT NOT NULL PRIMARY KEY);
SELECT no_such_column FROM no_such_table_for_migration_test;

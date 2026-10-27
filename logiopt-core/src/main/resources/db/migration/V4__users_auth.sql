-- V4: password storage and the single-admin rule for login and registration (Review 3 Phase 2).
-- password_hash keeps the Base64 PBKDF2 derived key; salt, iteration count and algorithm are stored
-- per user. Rows without an algorithm (the disabled demo user 1) can never sign in.
-- admin_marker is 1 for ADMIN and NULL otherwise; its UNIQUE key allows at most one ADMIN row.
-- One ALTER statement, so it is applied completely or not at all.

ALTER TABLE users
    ADD COLUMN password_salt       VARCHAR(64) NULL AFTER password_hash,
    ADD COLUMN password_iterations INT         NULL AFTER password_salt,
    ADD COLUMN password_algorithm  VARCHAR(40) NULL AFTER password_iterations,
    ADD COLUMN admin_marker        TINYINT GENERATED ALWAYS AS (CASE WHEN role = 'ADMIN' THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY uk_users_single_admin (admin_marker),
    ADD CONSTRAINT chk_users_password CHECK (password_algorithm IS NULL
        OR (password_salt IS NOT NULL AND password_iterations >= 100000));

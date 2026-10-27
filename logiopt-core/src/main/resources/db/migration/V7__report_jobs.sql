-- V7: persisted report jobs (Review 3 Phase 5). The V1 reports table is empty in Review 2
-- databases (no Review 2 code wrote it); rows that do not fit the keys and CHECKs below make V7
-- stop instead of being reinterpreted.
--
-- The database is the source of truth for a report's status. job_id is the public, unguessable id
-- used in URLs; user_id is the owner (the shipment's owner when the job was submitted). file_name
-- is the managed name of the written file inside the configured reports directory; it is set only
-- when the job is DONE and is never taken from a request. FAILED jobs carry an error message, and
-- finished_at is set exactly when the job is DONE or FAILED.

ALTER TABLE reports
    ADD COLUMN job_id CHAR(36) NOT NULL AFTER report_id,
    ADD COLUMN user_id BIGINT NOT NULL AFTER job_id,
    MODIFY COLUMN file_name VARCHAR(255) NULL,
    ADD COLUMN error_message VARCHAR(500) NULL AFTER status,
    MODIFY COLUMN created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN started_at TIMESTAMP(3) NULL AFTER created_at,
    ADD COLUMN finished_at TIMESTAMP(3) NULL AFTER started_at,
    ADD UNIQUE KEY uk_reports_job (job_id),
    ADD UNIQUE KEY uk_reports_file (file_name),
    ADD KEY idx_reports_user_created (user_id, created_at),
    ADD CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    ADD CONSTRAINT chk_reports_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    ADD CONSTRAINT chk_reports_file CHECK ((status = 'DONE') = (file_name IS NOT NULL)),
    ADD CONSTRAINT chk_reports_error CHECK ((status = 'FAILED') = (error_message IS NOT NULL)),
    ADD CONSTRAINT chk_reports_finished CHECK ((status IN ('DONE', 'FAILED')) = (finished_at IS NOT NULL));

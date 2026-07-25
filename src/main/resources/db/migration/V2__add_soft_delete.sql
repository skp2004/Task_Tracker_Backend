-- V2: Add soft-delete support to tt_tasks
-- Adds the deleted_at column used for the trash / recycle-bin feature.
-- NULL  → task is active  (matches @SQLRestriction("deleted_at IS NULL"))
-- value → task is in trash

ALTER TABLE tt_tasks
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ DEFAULT NULL;

-- Index to speed up trash queries for a given user
CREATE INDEX IF NOT EXISTS idx_tt_tasks_deleted_at
    ON tt_tasks (user_id, deleted_at)
    WHERE deleted_at IS NOT NULL;

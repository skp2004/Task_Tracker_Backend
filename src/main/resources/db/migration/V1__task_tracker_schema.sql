-- =============================================
-- TaskTracker Schema — V1
-- =============================================

-- ===== USERS =====
CREATE TABLE IF NOT EXISTS tt_users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    full_name       VARCHAR(255)  NOT NULL,
    phone           VARCHAR(20),
    avatar_url      VARCHAR(512),
    role            VARCHAR(10)   NOT NULL DEFAULT 'USER'
                    CHECK (role IN ('USER', 'ADMIN')),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tt_users_email ON tt_users (email);

-- ===== TASKS =====
CREATE TABLE IF NOT EXISTS tt_tasks (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES tt_users (id) ON DELETE CASCADE,
    title           VARCHAR(255)  NOT NULL,
    description     TEXT,
    task_date       DATE          NOT NULL,
    priority        VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM'
                    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status          VARCHAR(15)   NOT NULL DEFAULT 'TODO'
                    CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE')),
    category        VARCHAR(50),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tt_tasks_user_id       ON tt_tasks (user_id);
CREATE INDEX IF NOT EXISTS idx_tt_tasks_user_date     ON tt_tasks (user_id, task_date);
CREATE INDEX IF NOT EXISTS idx_tt_tasks_user_month    ON tt_tasks (user_id, DATE_TRUNC('month', task_date));
CREATE INDEX IF NOT EXISTS idx_tt_tasks_status        ON tt_tasks (user_id, status);

-- ===== updated_at trigger =====
CREATE OR REPLACE FUNCTION tt_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tt_users_updated_at
    BEFORE UPDATE ON tt_users
    FOR EACH ROW EXECUTE FUNCTION tt_set_updated_at();

CREATE TRIGGER tt_tasks_updated_at
    BEFORE UPDATE ON tt_tasks
    FOR EACH ROW EXECUTE FUNCTION tt_set_updated_at();

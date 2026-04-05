-- ─────────────────────────────────────────────────────────────────────────────
-- Timesheet Management  –  Auth Schema  (PostgreSQL / MySQL compatible)
-- ddl-auto: update  →  Hibernate manages the schema; this file is the reference.
-- Run manually only when pre-creating tables in a fresh DB.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── roles ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS roles (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE          -- ROLE_USER | ROLE_MANAGER | ROLE_ADMIN
);

INSERT INTO roles (name) VALUES ('ROLE_USER')    ON CONFLICT DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_MANAGER') ON CONFLICT DO NOTHING;
INSERT INTO roles (name) VALUES ('ROLE_ADMIN')   ON CONFLICT DO NOTHING;

-- ── users ─────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                  BIGSERIAL    PRIMARY KEY,
    first_name          VARCHAR(50)  NOT NULL,
    last_name           VARCHAR(50)  NOT NULL,
    username            VARCHAR(50)  NOT NULL UNIQUE,
    email               VARCHAR(100) NOT NULL UNIQUE,
    password            VARCHAR(255) NOT NULL,
    gender              VARCHAR(20),
    location            VARCHAR(100),
    designation         VARCHAR(100),
    manager_email       VARCHAR(100),
    type_of_employment  VARCHAR(20),
    photo_path          TEXT,
    -- ── Password reset / OTP fields ────────────────────────────────────────
    reset_otp_hash          VARCHAR(64),          -- SHA-256 hex of the 6-digit OTP
    reset_otp_expires_at    TIMESTAMP,            -- OTP validity window (issued_at + 10 min)
    reset_otp_attempts      INT          NOT NULL DEFAULT 0, -- locked at 5
    reset_otp_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    reset_token             VARCHAR(128),         -- SHA-256 hex of the reset token
    reset_token_expires_at  TIMESTAMP,            -- token validity window (verified_at + 15 min)
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

-- ── user_roles ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- ── refresh_tokens ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP    NOT NULL,
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ─────────────────────────────────────────────────────────────────────────────
-- PROJECT DOMAIN
-- ─────────────────────────────────────────────────────────────────────────────

-- ── projects ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS projects (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    description   VARCHAR(500),
    code          VARCHAR(20)  UNIQUE,          -- optional short identifier
    created_by_id BIGINT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT fk_proj_creator FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_projects_active ON projects (active);

-- ── project_assignments ───────────────────────────────────────────────────────
-- Tracks which users are assigned to which projects by a manager.
CREATE TABLE IF NOT EXISTS project_assignments (
    id             BIGSERIAL PRIMARY KEY,
    project_id     BIGINT    NOT NULL,
    user_id        BIGINT    NOT NULL,
    assigned_by_id BIGINT,
    assigned_at    TIMESTAMP,
    CONSTRAINT uq_project_user    UNIQUE (project_id, user_id),
    CONSTRAINT fk_pa_project      FOREIGN KEY (project_id)     REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_user         FOREIGN KEY (user_id)        REFERENCES users    (id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_assigned_by  FOREIGN KEY (assigned_by_id) REFERENCES users    (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pa_user_id    ON project_assignments (user_id);
CREATE INDEX IF NOT EXISTS idx_pa_project_id ON project_assignments (project_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- TIMESHEET DOMAIN
-- ─────────────────────────────────────────────────────────────────────────────

-- ── timesheets ────────────────────────────────────────────────────────────────
-- status: DRAFT → SUBMITTED → APPROVED | REJECTED
CREATE TABLE IF NOT EXISTS timesheets (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    title            VARCHAR(150),
    period_start     DATE,
    period_end       DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    submitted_at     TIMESTAMP,
    reviewed_at      TIMESTAMP,
    reviewer_comment VARCHAR(500),
    reviewed_by_id   BIGINT,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP,
    CONSTRAINT fk_ts_user        FOREIGN KEY (user_id)        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ts_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_ts_user_id ON timesheets (user_id);
CREATE INDEX IF NOT EXISTS idx_ts_status  ON timesheets (status);

-- ── timesheet_entries ─────────────────────────────────────────────────────────
-- One entry = one day of work on one project within one timesheet.
-- Unique: a project cannot be logged twice on the same date in the same timesheet.
CREATE TABLE IF NOT EXISTS timesheet_entries (
    id           BIGSERIAL    PRIMARY KEY,
    timesheet_id BIGINT       NOT NULL,
    project_id   BIGINT       NOT NULL,
    work_date    DATE         NOT NULL,
    hours_worked NUMERIC(4,2) NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT uq_ts_project_date  UNIQUE (timesheet_id, project_id, work_date),
    CONSTRAINT fk_te_timesheet     FOREIGN KEY (timesheet_id) REFERENCES timesheets (id) ON DELETE CASCADE,
    CONSTRAINT fk_te_project       FOREIGN KEY (project_id)   REFERENCES projects   (id)
);

CREATE INDEX IF NOT EXISTS idx_te_timesheet_id ON timesheet_entries (timesheet_id);
CREATE INDEX IF NOT EXISTS idx_te_project_id   ON timesheet_entries (project_id);
CREATE INDEX IF NOT EXISTS idx_te_work_date    ON timesheet_entries (work_date);

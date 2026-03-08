-- Scheduling / Batch-Jobs
CREATE TABLE batch_job_definition (
    id              SERIAL PRIMARY KEY,
    job_key         VARCHAR(100) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    job_class       VARCHAR(500) NOT NULL,
    schedule_type   VARCHAR(20) NOT NULL DEFAULT 'NONE',
    cron_expression VARCHAR(100),
    interval_seconds INTEGER,
    enabled         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE batch_job_execution_log (
    id                  BIGSERIAL PRIMARY KEY,
    job_definition_id   INTEGER NOT NULL REFERENCES batch_job_definition(id),
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    error_message       TEXT,
    records_affected    INTEGER,
    log_file            VARCHAR(500),
    triggered_by        VARCHAR(50) NOT NULL DEFAULT 'SCHEDULER'
);
CREATE INDEX idx_exec_log_job_id ON batch_job_execution_log(job_definition_id);
CREATE INDEX idx_exec_log_start ON batch_job_execution_log(start_time DESC);

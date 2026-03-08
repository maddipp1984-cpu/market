-- Neue Tabelle batch_schedule (ersetzt batch_job_definition)
CREATE TABLE batch_schedule (
    id SERIAL PRIMARY KEY,
    job_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    schedule_type VARCHAR(20) DEFAULT 'NONE',
    cron_expression VARCHAR(100),
    interval_seconds INTEGER,
    enabled BOOLEAN DEFAULT false,
    parameters JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_batch_schedule_job_key ON batch_schedule(job_key);

-- batch_job_execution_log anpassen: FK auf batch_schedule statt batch_job_definition
ALTER TABLE batch_job_execution_log
    DROP CONSTRAINT IF EXISTS batch_job_execution_log_job_definition_id_fkey;
ALTER TABLE batch_job_execution_log
    RENAME COLUMN job_definition_id TO schedule_id;
ALTER TABLE batch_job_execution_log
    ADD CONSTRAINT batch_job_execution_log_schedule_id_fkey
    FOREIGN KEY (schedule_id) REFERENCES batch_schedule(id);

-- Daten migrieren (bestehende Definitionen → Schedules)
INSERT INTO batch_schedule (id, job_key, name, schedule_type, cron_expression, interval_seconds, enabled, parameters)
SELECT id, job_key, name, schedule_type, cron_expression, interval_seconds, enabled, '{}'::jsonb
FROM batch_job_definition;

-- Alte Tabelle droppen
DROP TABLE batch_job_definition;

-- Sequence synchronisieren
SELECT setval('batch_schedule_id_seq', COALESCE((SELECT MAX(id) FROM batch_schedule), 0) + 1, false);

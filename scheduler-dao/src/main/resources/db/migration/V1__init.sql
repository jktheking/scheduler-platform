-- scheduler-platform schema v1 (minimal for API -> master -> ready)
-- NOTE: keep tables small and evolve with backward-compatible migrations.

CREATE TABLE IF NOT EXISTS t_command (
  command_id        TEXT PRIMARY KEY,
  tenant_id         TEXT NOT NULL,
  idempotency_key   TEXT NOT NULL,
  command_type      TEXT NOT NULL,
  workflow_code     BIGINT NOT NULL,
  workflow_version  INTEGER NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL,
  payload_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
  status            TEXT NOT NULL DEFAULT 'NEW',
  last_error_message TEXT NULL
);


-- Idempotency per tenant+key
CREATE UNIQUE INDEX IF NOT EXISTS ux_command_tenant_idempotency
  ON t_command(tenant_id, idempotency_key);

CREATE INDEX IF NOT EXISTS ix_command_status_created
  ON t_command(status, created_at);

-- Master-side processed-dedupe to protect against Kafka at-least-once delivery
CREATE TABLE IF NOT EXISTS t_command_dedupe (
  command_id    TEXT PRIMARY KEY,
  processed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  outcome       TEXT NOT NULL,
  detail        TEXT NULL
);

CREATE TABLE IF NOT EXISTS t_workflow_instance (
  workflow_instance_id BIGSERIAL PRIMARY KEY,
  tenant_id            TEXT NOT NULL,
  workflow_code        BIGINT NOT NULL,
  workflow_version     INTEGER NOT NULL,
  status               TEXT NOT NULL,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  schedule_time        TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_wi_tenant_code_status
  ON t_workflow_instance(tenant_id, workflow_code, status);

CREATE TABLE IF NOT EXISTS t_workflow_plan (
  workflow_instance_id BIGINT PRIMARY KEY REFERENCES t_workflow_instance(workflow_instance_id) ON DELETE CASCADE,
  plan_json            JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS t_trigger (
  trigger_id           BIGSERIAL PRIMARY KEY,
  workflow_instance_id BIGINT NOT NULL REFERENCES t_workflow_instance(workflow_instance_id) ON DELETE CASCADE,
  due_time             TIMESTAMPTZ NOT NULL,
  status               TEXT NOT NULL, -- DUE | ENQUEUED | PROCESSING | DONE | FAILED
  claimed_by           TEXT NULL,
  claimed_at           TIMESTAMPTZ NULL,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_trigger_status_due
  ON t_trigger(status, due_time);

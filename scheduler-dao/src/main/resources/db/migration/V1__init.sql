-- =====================================================================
-- Scheduler Platform - Consolidated Flyway Migration
-- Version: V1
--
-- Semantics preserved:
--  - Command WAL tables (API -> Kafka WAL -> Master persistence)
--  - Workflow runtime tables (instances, plan)
--  - Trigger table for durable trigger engines (Quartz/TimeWheel)
--  - DAG definitions + runtime task instances
--  - DLQ storage
--  - Sequences for allocating workflow_code/task_code
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Command WAL (Kafka WAL-first ingestion)
-- ---------------------------------------------------------------------

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
  schedule_time        TIMESTAMPTZ NOT NULL,
  last_error           TEXT NULL
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
  shard_id            INTEGER NOT NULL DEFAULT 0,
  due_time             TIMESTAMPTZ NOT NULL,
  trigger_type         TEXT NOT NULL DEFAULT 'DAG_WAKE',
  status               TEXT NOT NULL, -- DUE | ENQUEUED | PROCESSING | DONE | FAILED
  claimed_by           TEXT NULL,
  claimed_at           TIMESTAMPTZ NULL,
  last_error           TEXT NULL,
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_trigger_status_due
  ON t_trigger(status, due_time);

-- One workflow SLA trigger per workflow instance (idempotent scheduling).
CREATE UNIQUE INDEX IF NOT EXISTS ux_trigger_workflow_sla
  ON t_trigger(workflow_instance_id)
  WHERE trigger_type = 'WORKFLOW_SLA';

-- ---------------------------------------------------------------------
-- 2) DAG definition storage + runtime (master-owned DAG orchestration)
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS t_workflow_definition (
  workflow_code BIGINT NOT NULL,
  workflow_version INTEGER NOT NULL,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  definition_json JSONB NOT NULL,
  PRIMARY KEY(workflow_code, workflow_version)
);

CREATE TABLE IF NOT EXISTS t_task_definition (
  task_code BIGINT NOT NULL,
  task_version INTEGER NOT NULL,
  tenant_id TEXT NOT NULL,
  name TEXT NOT NULL,
  task_type TEXT NOT NULL,
  definition_json JSONB NOT NULL,
  PRIMARY KEY(task_code, task_version)
);

CREATE TABLE IF NOT EXISTS t_workflow_dag_edge (
  workflow_code BIGINT NOT NULL,
  workflow_version INTEGER NOT NULL,
  from_task_code BIGINT NOT NULL,
  to_task_code BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_wde_workflow
  ON t_workflow_dag_edge(workflow_code, workflow_version);

CREATE INDEX IF NOT EXISTS ix_wde_to
  ON t_workflow_dag_edge(to_task_code);

CREATE INDEX IF NOT EXISTS ix_wde_from
  ON t_workflow_dag_edge(from_task_code);

-- runtime
CREATE TABLE IF NOT EXISTS t_task_instance (
  task_instance_id BIGSERIAL PRIMARY KEY,
  workflow_instance_id BIGINT NOT NULL REFERENCES t_workflow_instance(workflow_instance_id) ON DELETE CASCADE,
  shard_id INTEGER NOT NULL DEFAULT 0,
  task_code BIGINT NOT NULL,
  task_version INTEGER NOT NULL,
  task_name TEXT NOT NULL,
  task_type TEXT NOT NULL,
  status TEXT NOT NULL,
  attempt INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 3,
  next_run_time TIMESTAMPTZ NULL,
  worker_id TEXT NULL,
  claimed_at TIMESTAMPTZ NULL,
  started_at TIMESTAMPTZ NULL,
  finished_at TIMESTAMPTZ NULL,
  last_error TEXT NULL,
  timeout_alerted_at TIMESTAMPTZ NULL,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(workflow_instance_id, task_code, attempt)
);

CREATE INDEX IF NOT EXISTS ix_ti_workflow
  ON t_task_instance(workflow_instance_id);

CREATE INDEX IF NOT EXISTS ix_ti_status_next
  ON t_task_instance(status, next_run_time);

CREATE INDEX IF NOT EXISTS ix_ti_timeout_alerted
  ON t_task_instance(status, timeout_alerted_at);

CREATE TABLE IF NOT EXISTS t_dlq_task (
  dlq_id BIGSERIAL PRIMARY KEY,
  workflow_instance_id BIGINT,
  task_instance_id BIGINT,
  reason TEXT,
  payload_json JSONB,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_dlq_workflow
  ON t_dlq_task(workflow_instance_id);

CREATE INDEX IF NOT EXISTS ix_dlq_created
  ON t_dlq_task(created_at);

-- ---------------------------------------------------------------------
-- 3) Sequences for deterministic code allocation (workflow/task codes)
-- ---------------------------------------------------------------------
-- Sequences for generating workflow_code and task_code.

CREATE SEQUENCE IF NOT EXISTS seq_workflow_code
  START WITH 900000
  INCREMENT BY 1
  CACHE 50;

CREATE SEQUENCE IF NOT EXISTS seq_task_code
  START WITH 910000
  INCREMENT BY 1
  CACHE 50;


CREATE INDEX IF NOT EXISTS idx_t_trigger_shard_status_due
  ON t_trigger (shard_id, status, due_time, trigger_id);

CREATE INDEX IF NOT EXISTS idx_t_task_instance_shard_status_updated
  ON t_task_instance (shard_id, status, updated_at, task_instance_id);

CREATE INDEX IF NOT EXISTS idx_t_task_instance_shard_status_next_run
  ON t_task_instance (shard_id, status, next_run_time, task_instance_id);


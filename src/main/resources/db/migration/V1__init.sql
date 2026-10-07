-- objetivos a largo plazo
CREATE TABLE goal (
    id          BIGSERIAL    PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    target_date DATE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_goal_title CHECK (length(btrim(title)) > 0)
);

-- sin recurrence_rule la tarea es ocasional
CREATE TABLE task (
    id              BIGSERIAL     PRIMARY KEY,
    goal_id         BIGINT        REFERENCES goal(id) ON DELETE SET NULL,
    title           VARCHAR(200)  NOT NULL,
    unit            VARCHAR(50)   NOT NULL,
    target_value    NUMERIC(12,2) NOT NULL,
    recurrence_rule VARCHAR(255),
    start_date      DATE          NOT NULL,
    end_date        DATE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_task_title  CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_task_unit   CHECK (length(btrim(unit)) > 0),
    CONSTRAINT ck_task_target CHECK (target_value > 0),
    CONSTRAINT ck_task_dates  CHECK (end_date IS NULL OR end_date >= start_date)
);

-- una tarea en un día concreto (lo que pinta la barra)
CREATE TABLE task_occurrence (
    id              BIGSERIAL     PRIMARY KEY,
    task_id         BIGINT        NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    occurrence_date DATE          NOT NULL,
    target_value    NUMERIC(12,2) NOT NULL,
    current_value   NUMERIC(12,2) NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',

    CONSTRAINT uq_task_date          UNIQUE (task_id, occurrence_date),
    CONSTRAINT ck_occurrence_status  CHECK (status IN ('PENDING', 'DONE', 'MISSED')),
    CONSTRAINT ck_occurrence_target  CHECK (target_value > 0),
    CONSTRAINT ck_occurrence_current CHECK (current_value >= 0)
);

-- cada avance; amount negativo sirve para corregir
CREATE TABLE progress_entry (
    id            BIGSERIAL     PRIMARY KEY,
    occurrence_id BIGINT        NOT NULL REFERENCES task_occurrence(id) ON DELETE CASCADE,
    amount        NUMERIC(12,2) NOT NULL,
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_progress_amount CHECK (amount <> 0)
);

CREATE INDEX idx_task_goal              ON task (goal_id);
CREATE INDEX idx_progress_occurrence    ON progress_entry (occurrence_id);
CREATE INDEX idx_occurrence_date        ON task_occurrence (occurrence_date);
CREATE INDEX idx_occurrence_status_date ON task_occurrence (status, occurrence_date);
CREATE INDEX idx_progress_created       ON progress_entry (created_at);
-- permite el estado SKIPPED (día omitido de una tarea recurrente)
ALTER TABLE task_occurrence DROP CONSTRAINT ck_occurrence_status;
ALTER TABLE task_occurrence
    ADD CONSTRAINT ck_occurrence_status
    CHECK (status IN ('PENDING', 'DONE', 'MISSED', 'SKIPPED'));
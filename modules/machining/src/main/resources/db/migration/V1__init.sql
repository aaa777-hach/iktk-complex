-- ИКТК: модуль механообработки. Начальная миграция.

CREATE TABLE machining_process (
    id                       VARCHAR(64)  PRIMARY KEY,
    project_id               VARCHAR(64)  NOT NULL,
    object_ref               VARCHAR(256) NOT NULL,
    setups                   JSONB,
    operations               JSONB,
    stages                   JSONB,
    manufacturability_report JSONB,
    created_at               TIMESTAMP    NOT NULL
);
CREATE INDEX idx_machining_process_project_id ON machining_process (project_id);
CREATE INDEX idx_machining_process_object_ref ON machining_process (object_ref);

CREATE TABLE cnc_program (
    id               VARCHAR(64)  PRIMARY KEY,
    project_id       VARCHAR(64)  NOT NULL,
    process_id       VARCHAR(64)  NOT NULL,
    equipment_id     VARCHAR(64),
    equipment_model  VARCHAR(256),
    postprocessor    VARCHAR(64),
    program_uri      VARCHAR(512),
    program_name     VARCHAR(256),
    line_count       INTEGER,
    byte_size        INTEGER,
    operations       JSONB,
    setup_sheet      JSONB,
    validation       JSONB,
    created_at       TIMESTAMP    NOT NULL
);
CREATE INDEX idx_cnc_program_project_id ON cnc_program (project_id);
CREATE INDEX idx_cnc_program_process_id ON cnc_program (process_id);
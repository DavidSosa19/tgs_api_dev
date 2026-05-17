-- =============================================================================
-- V4 – Multi-tenancy: company_id FK en schedule_template y route_operation
-- Ejecutar manualmente contra la BD antes de desplegar el build correspondiente.
-- Backfill automático desde route.company_id (ambas entidades ya tienen route_id).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. core.schedule_template
-- ---------------------------------------------------------------------------
ALTER TABLE core.schedule_template
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

-- Hereda la empresa de la ruta principal
UPDATE core.schedule_template st
   SET company_id = (SELECT r.company_id FROM core.route r WHERE r.id = st.route_id)
 WHERE st.company_id IS NULL;

ALTER TABLE core.schedule_template
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_schedule_template_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_schedule_template_company_id
    ON core.schedule_template (company_id);

-- ---------------------------------------------------------------------------
-- 2. core.route_operation
-- ---------------------------------------------------------------------------
ALTER TABLE core.route_operation
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

-- Hereda la empresa de la ruta
UPDATE core.route_operation ro
   SET company_id = (SELECT r.company_id FROM core.route r WHERE r.id = ro.route_id)
 WHERE ro.company_id IS NULL;

ALTER TABLE core.route_operation
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_route_operation_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_route_operation_company_id
    ON core.route_operation (company_id);

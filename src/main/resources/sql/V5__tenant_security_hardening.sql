-- =============================================================================
-- V5 – Tenant security hardening: company_id on all remaining business entities
-- Ejecutar DESPUÉS de V4.
--
-- Entities covered (backfill chain):
--   P1:  vehicle_assignment  ← route_operation.company_id
--        rotation_entry      ← vehicle_rotation.company_id
--   P2:  schedule            ← vehicle_assignment.company_id
--        operation_event     ← route_operation.company_id
--        vehicle_maintenance ← vehicle.company_id
--        vehicle_status_history ← vehicle.company_id
--        driver_assignment   ← vehicle_assignment.company_id
--        driver_assignment_history ← vehicle.company_id
--        actual_departure    ← schedule.company_id  (backfilled after schedule)
-- =============================================================================

-- ---------------------------------------------------------------------------
-- P1-1. core.vehicle_assignment
-- ---------------------------------------------------------------------------
ALTER TABLE core.vehicle_assignment
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.vehicle_assignment va
   SET company_id = (
       SELECT ro.company_id
         FROM core.route_operation ro
        WHERE ro.id = va.route_operation_id
   )
 WHERE va.company_id IS NULL;

ALTER TABLE core.vehicle_assignment
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_vehicle_assignment_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_vehicle_assignment_company_id
    ON core.vehicle_assignment (company_id);

-- ---------------------------------------------------------------------------
-- P1-2. core.rotation_entry
-- ---------------------------------------------------------------------------
ALTER TABLE core.rotation_entry
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.rotation_entry re
   SET company_id = (
       SELECT vr.company_id
         FROM core.vehicle_rotation vr
        WHERE vr.id = re.rotation_id
   )
 WHERE re.company_id IS NULL;

ALTER TABLE core.rotation_entry
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_rotation_entry_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_rotation_entry_company_id
    ON core.rotation_entry (company_id);

-- ---------------------------------------------------------------------------
-- P2-1. core.schedule
-- ---------------------------------------------------------------------------
ALTER TABLE core.schedule
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.schedule s
   SET company_id = (
       SELECT va.company_id
         FROM core.vehicle_assignment va
        WHERE va.id = s.vehicle_assignment_id
   )
 WHERE s.company_id IS NULL;

ALTER TABLE core.schedule
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_schedule_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_schedule_company_id
    ON core.schedule (company_id);

-- ---------------------------------------------------------------------------
-- P2-2. core.operation_event
-- ---------------------------------------------------------------------------
ALTER TABLE core.operation_event
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.operation_event oe
   SET company_id = (
       SELECT ro.company_id
         FROM core.route_operation ro
        WHERE ro.id = oe.route_operation_id
   )
 WHERE oe.company_id IS NULL;

ALTER TABLE core.operation_event
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_operation_event_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_operation_event_company_id
    ON core.operation_event (company_id);

-- ---------------------------------------------------------------------------
-- P2-3. core.vehicle_maintenance
-- ---------------------------------------------------------------------------
ALTER TABLE core.vehicle_maintenance
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.vehicle_maintenance vm
   SET company_id = (
       SELECT v.company_id
         FROM core.vehicle v
        WHERE v.id = vm.vehicle_id
   )
 WHERE vm.company_id IS NULL;

ALTER TABLE core.vehicle_maintenance
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_vehicle_maintenance_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_vehicle_maintenance_company_id
    ON core.vehicle_maintenance (company_id);

-- ---------------------------------------------------------------------------
-- P2-4. core.vehicle_status_history
-- ---------------------------------------------------------------------------
ALTER TABLE core.vehicle_status_history
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.vehicle_status_history vsh
   SET company_id = (
       SELECT v.company_id
         FROM core.vehicle v
        WHERE v.id = vsh.vehicle_id
   )
 WHERE vsh.company_id IS NULL;

ALTER TABLE core.vehicle_status_history
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_vehicle_status_history_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_vehicle_status_history_company_id
    ON core.vehicle_status_history (company_id);

-- ---------------------------------------------------------------------------
-- P2-5. core.driver_assignment
-- ---------------------------------------------------------------------------
ALTER TABLE core.driver_assignment
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.driver_assignment da
   SET company_id = (
       SELECT va.company_id
         FROM core.vehicle_assignment va
        WHERE va.id = da.vehicle_assignment_id
   )
 WHERE da.company_id IS NULL;

ALTER TABLE core.driver_assignment
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_driver_assignment_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_driver_assignment_company_id
    ON core.driver_assignment (company_id);

-- ---------------------------------------------------------------------------
-- P2-6. core.driver_assignment_history
-- ---------------------------------------------------------------------------
ALTER TABLE core.driver_assignment_history
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.driver_assignment_history dah
   SET company_id = (
       SELECT v.company_id
         FROM core.vehicle v
        WHERE v.id = dah.vehicle_id
   )
 WHERE dah.company_id IS NULL;

ALTER TABLE core.driver_assignment_history
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_driver_assignment_history_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_driver_assignment_history_company_id
    ON core.driver_assignment_history (company_id);

-- ---------------------------------------------------------------------------
-- P2-7. core.actual_departure  (after schedule has company_id)
-- ---------------------------------------------------------------------------
ALTER TABLE core.actual_departure
    ADD COLUMN IF NOT EXISTS company_id INTEGER;

UPDATE core.actual_departure ad
   SET company_id = (
       SELECT s.company_id
         FROM core.schedule s
        WHERE s.id = ad.schedule_id
   )
 WHERE ad.company_id IS NULL;

ALTER TABLE core.actual_departure
    ALTER COLUMN company_id SET NOT NULL,
    ADD CONSTRAINT fk_actual_departure_company
        FOREIGN KEY (company_id) REFERENCES core.company(id);

CREATE INDEX IF NOT EXISTS idx_actual_departure_company_id
    ON core.actual_departure (company_id);

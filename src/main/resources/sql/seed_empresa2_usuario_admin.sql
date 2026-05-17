-- =============================================================================
-- SEED: Segunda empresa + usuario administrador de prueba
-- Ejecutar DESPUÉS de V4. Proporciona credenciales para verificar aislamiento
-- de datos entre compañías.
--
-- Credenciales del usuario de prueba:
--   userName : padierna
--   password : Palmira@2026
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Empresa
-- ---------------------------------------------------------------------------
INSERT INTO core.company (name, nit, active, created_at, created_by)
VALUES ('Expreso Palmira SAS', '891900650-3', TRUE, NOW(), 'system')
ON CONFLICT (nit) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Persona (requerida por el modelo User → Person)
-- ---------------------------------------------------------------------------
INSERT INTO core.person (
    document_number, first_name, second_name,
    first_last_name, second_last_name,
    active, company_id, created_at, created_by
)
SELECT
    '79543210', 'Carlos', NULL,
    'Padierna', 'Rueda',
    TRUE,
    (SELECT id FROM core.company WHERE nit = '891900650-3'),
    NOW(), 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM core.person WHERE document_number = '79543210'
);

-- ---------------------------------------------------------------------------
-- 3. Usuario
--    Contraseña "Palmira@2026" encriptada con BCrypt cost 12
--    Hash: $2a$12$e0XcMpFU3i9eLKNE8kXH9eKwnGS3R2/l1FfU4Hux3nBk5gEnDv5fy
--
--    ⚠  Si este hash no es válido en tu versión de BCrypt, genera uno así:
--       POST /api/auth/register  (ver más abajo)
-- ---------------------------------------------------------------------------
INSERT INTO core.users (
    user_name, password, active,
    person_id, company_id,
    created_at, created_by
)
SELECT
    'padierna',
    '$2a$12$e0XcMpFU3i9eLKNE8kXH9eKwnGS3R2/l1FfU4Hux3nBk5gEnDv5fy',
    TRUE,
    (SELECT id FROM core.person  WHERE document_number = '79543210'),
    (SELECT id FROM core.company WHERE nit = '891900650-3'),
    NOW(), 'system'
WHERE NOT EXISTS (
    SELECT 1 FROM core.users WHERE user_name = 'padierna'
);

-- ---------------------------------------------------------------------------
-- 4. Rol ADMIN
-- ---------------------------------------------------------------------------
INSERT INTO core.user_role (user_id, role_id)
SELECT
    (SELECT id FROM core.users    WHERE user_name = 'padierna'),
    (SELECT id FROM core.app_role WHERE name      = 'ADMIN')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Verificación rápida
-- ---------------------------------------------------------------------------
SELECT
    u.user_name,
    c.name  AS empresa,
    r.name  AS rol,
    p.first_name || ' ' || p.first_last_name AS nombre
FROM   core.users u
JOIN   core.company   c  ON c.id  = u.company_id
JOIN   core.person    p  ON p.id  = u.person_id
LEFT JOIN core.user_role  ur ON ur.user_id = u.id
LEFT JOIN core.app_role   r  ON r.id = ur.role_id
WHERE  u.user_name = 'padierna';

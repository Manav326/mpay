-- Align Customer Care with the explicit-grant model without revoking effective access
-- from existing Manager employees.
--
-- V33 granted SUPPORT_VIEW and SUPPORT_MANAGE to MANAGER at role level. The current
-- Customer Care model requires explicit grants. Existing Manager employees therefore
-- receive employee-level ALLOW overrides before the role defaults are removed. Existing
-- DENY/ALLOW overrides are preserved; only employees with no override are migrated.
DO $$
DECLARE
    v_admin_employee_id BIGINT;
BEGIN
    SELECT id
      INTO v_admin_employee_id
      FROM employees
     WHERE UPPER(role) = 'ADMIN'
     ORDER BY id
     LIMIT 1;

    IF v_admin_employee_id IS NULL THEN
        RAISE EXCEPTION 'Customer Care access migration requires an ADMIN employee.';
    END IF;

    INSERT INTO employee_permission_overrides (
        employee_id, permission, allowed, changed_by_employee_id, created_at, updated_at
    )
    SELECT e.id, 'SUPPORT_VIEW', TRUE, v_admin_employee_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM employees e
     WHERE UPPER(e.role) = 'MANAGER'
       AND NOT EXISTS (
           SELECT 1
             FROM employee_permission_overrides o
            WHERE o.employee_id = e.id
              AND UPPER(o.permission) = 'SUPPORT_VIEW'
       );

    INSERT INTO employee_permission_overrides (
        employee_id, permission, allowed, changed_by_employee_id, created_at, updated_at
    )
    SELECT e.id, 'SUPPORT_MANAGE', TRUE, v_admin_employee_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM employees e
     WHERE UPPER(e.role) = 'MANAGER'
       AND NOT EXISTS (
           SELECT 1
             FROM employee_permission_overrides o
            WHERE o.employee_id = e.id
              AND UPPER(o.permission) = 'SUPPORT_MANAGE'
       );

    DELETE FROM role_permissions
     WHERE UPPER(role) = 'MANAGER'
       AND UPPER(permission) IN ('SUPPORT_VIEW', 'SUPPORT_MANAGE');
END $$;

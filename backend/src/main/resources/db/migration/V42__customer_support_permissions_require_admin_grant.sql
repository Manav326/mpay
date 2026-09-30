-- Customer Care employees start with sign-in access only.
-- The administrator explicitly grants support, customer-context, voice and AI capabilities.
DELETE FROM role_permissions
WHERE role = 'CUSTOMER_SUPPORT'
  AND permission IN (
      'SUPPORT_VIEW',
      'SUPPORT_MANAGE',
      'CALL_CUSTOMER',
      'MANAGE_CALL_ACCESS',
      'MANAGE_SUPPORT_AI',
      'SUPPORT_VIEW_CUSTOMER_CONTEXT'
  );

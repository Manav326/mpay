-- Employee accounts use the shared account-id namespace, so legacy admin review
-- columns must not retain a foreign key directly to the customer users table.
-- The reviewed_by value is still authoritative because users and employees share
-- the global users_id_seq id namespace after V40.
ALTER TABLE history_pdf_access_requests
    DROP CONSTRAINT IF EXISTS history_pdf_access_requests_reviewed_by_fkey;

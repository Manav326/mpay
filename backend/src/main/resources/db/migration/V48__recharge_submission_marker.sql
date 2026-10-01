ALTER TABLE recharge_transactions
    ADD COLUMN provider_submission_started_at TIMESTAMPTZ;

UPDATE recharge_transactions
SET provider_submission_started_at = COALESCE(updated_at, created_at)
WHERE status = 'PENDING'
  AND provider_reference IS NOT NULL;

ALTER TABLE wallets
    ADD COLUMN reserved_balance NUMERIC(19,2) NOT NULL DEFAULT 0;

ALTER TABLE recharge_transactions
    ADD COLUMN client_request_id VARCHAR(100);

ALTER TABLE recharge_transactions
    ADD COLUMN operator VARCHAR(30);

ALTER TABLE recharge_transactions
    ADD COLUMN circle VARCHAR(100);

ALTER TABLE recharge_transactions
    ADD COLUMN provider_name VARCHAR(50) NOT NULL DEFAULT 'MOCK';

ALTER TABLE recharge_transactions
    ADD COLUMN provider_reference VARCHAR(150);

ALTER TABLE recharge_transactions
    ADD COLUMN message VARCHAR(500);

ALTER TABLE recharge_transactions
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE recharge_transactions
SET client_request_id = transaction_id
WHERE client_request_id IS NULL;

UPDATE recharge_transactions
SET operator = 'UNKNOWN'
WHERE operator IS NULL;

UPDATE recharge_transactions
SET circle = 'UNKNOWN'
WHERE circle IS NULL;

ALTER TABLE recharge_transactions
    ALTER COLUMN client_request_id SET NOT NULL;

ALTER TABLE recharge_transactions
    ALTER COLUMN operator SET NOT NULL;

ALTER TABLE recharge_transactions
    ALTER COLUMN circle SET NOT NULL;

CREATE UNIQUE INDEX uq_recharge_client_request_user
    ON recharge_transactions(client_request_id, user_id);

CREATE INDEX idx_recharge_provider_reference
    ON recharge_transactions(provider_reference);

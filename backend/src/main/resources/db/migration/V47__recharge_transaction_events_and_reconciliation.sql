CREATE TABLE recharge_transaction_events (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL REFERENCES recharge_transactions(transaction_id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    provider_reference VARCHAR(150),
    wallet_ledger_ref VARCHAR(150),
    wallet_amount NUMERIC(19,2),
    message VARCHAR(1000),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_recharge_event_transaction_time
    ON recharge_transaction_events(transaction_id, occurred_at, id);

CREATE INDEX idx_recharge_event_time
    ON recharge_transaction_events(occurred_at);

INSERT INTO recharge_transaction_events (
    transaction_id, from_status, to_status, event_type, provider_reference,
    wallet_ledger_ref, wallet_amount, message, occurred_at
)
SELECT transaction_id, NULL, status, 'LEGACY_CURRENT_STATE', provider_reference,
       wallet_ledger_ref, wallet_debit_amount, message,
       COALESCE(completed_at, updated_at, created_at)
FROM recharge_transactions;

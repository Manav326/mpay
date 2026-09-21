ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(30) NOT NULL DEFAULT 'ADD_MONEY';

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS recharge_mobile_number VARCHAR(15);

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS recharge_operator VARCHAR(30);

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS recharge_circle VARCHAR(100);

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS recharge_plan_id VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_payment_orders_purpose
    ON payment_orders(purpose);

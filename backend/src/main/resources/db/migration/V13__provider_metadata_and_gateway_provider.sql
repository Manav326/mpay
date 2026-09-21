ALTER TABLE recharge_offer_cache
    ADD COLUMN IF NOT EXISTS provider_metadata VARCHAR(3000);

ALTER TABLE payment_orders
    ADD COLUMN IF NOT EXISTS provider_name VARCHAR(40) NOT NULL DEFAULT 'razorpay';

CREATE INDEX IF NOT EXISTS idx_payment_orders_provider_name
    ON payment_orders(provider_name);

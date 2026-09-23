-- Persist the optional recharge recipient label entered by the user.
ALTER TABLE recharge_transactions
    ADD COLUMN recipient_name VARCHAR(120);

-- Keep the same label with gateway-funded recharge orders until settlement creates the recharge transaction.
ALTER TABLE payment_orders
    ADD COLUMN recharge_recipient_name VARCHAR(120);

-- Vendor payout profile: bank identity and which payout method is primary.
ALTER TABLE rental_vendors
    ADD COLUMN bank_name VARCHAR(120);

ALTER TABLE rental_vendors
    ADD COLUMN payout_primary_method VARCHAR(10);

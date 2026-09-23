-- Persist recharge recipient identity and vendor payout profile details.
ALTER TABLE recharge_transactions ADD COLUMN recipient_name VARCHAR(120);
ALTER TABLE payment_orders ADD COLUMN recharge_recipient_name VARCHAR(120);
ALTER TABLE rental_vendors ADD COLUMN bank_name VARCHAR(120);
ALTER TABLE rental_vendors ADD COLUMN payout_primary_method VARCHAR(10);

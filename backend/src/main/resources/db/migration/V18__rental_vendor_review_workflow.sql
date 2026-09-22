ALTER TABLE rental_vendors ADD COLUMN rejection_reason VARCHAR(500);
ALTER TABLE rental_cars ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE rental_drivers ADD COLUMN rejection_reason VARCHAR(500);

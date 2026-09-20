ALTER TABLE password_reset_otps
    ALTER COLUMN otp_hash DROP NOT NULL;

ALTER TABLE password_reset_otps
    ADD COLUMN verification_sid VARCHAR(34);

CREATE INDEX idx_password_reset_otps_verification_sid
    ON password_reset_otps(verification_sid);

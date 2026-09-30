-- Split staff actor references from customer account references.
-- Customer ownership references (wallets, payments, bookings, vendors, etc.)
-- remain in users and are not modified by this migration.

ALTER TABLE history_pdf_access_requests
    DROP CONSTRAINT IF EXISTS history_pdf_access_requests_reviewed_by_fkey;

ALTER TABLE rental_vendor_review_history
    DROP CONSTRAINT IF EXISTS rental_vendor_review_history_actor_user_id_fkey;

ALTER TABLE rental_car_review_history
    DROP CONSTRAINT IF EXISTS rental_car_review_history_actor_user_id_fkey;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM history_pdf_access_requests
        WHERE reviewed_by IS NOT NULL AND reviewed_by NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        )
    ) THEN
        RAISE EXCEPTION 'Employee reference migration stopped: unexpected history PDF reviewer id.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM support_cases
        WHERE assigned_user_id IS NOT NULL AND assigned_user_id NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        )
    ) THEN
        RAISE EXCEPTION 'Employee reference migration stopped: unexpected support case employee id.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM support_notes
        WHERE author_user_id IS NOT NULL AND author_user_id NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        )
    ) THEN
        RAISE EXCEPTION 'Employee reference migration stopped: unexpected support-note employee id.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM support_call_requests
        WHERE (reviewed_by_user_id IS NOT NULL AND reviewed_by_user_id NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        ))
           OR (assigned_user_id IS NOT NULL AND assigned_user_id NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        ))
    ) THEN
        RAISE EXCEPTION 'Employee reference migration stopped: unexpected support callback employee id.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM support_messages
        WHERE sender_type = 'STAFF'
          AND sender_user_id IS NOT NULL
          AND sender_user_id NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        )
    ) THEN
        RAISE EXCEPTION 'Employee reference migration stopped: unexpected support staff sender id.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM voice_calls
        WHERE caller_user_id NOT IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        )
    ) THEN
        RAISE EXCEPTION 'Employee reference migration stopped: unexpected voice-call employee id.';
    END IF;
END $$;

ALTER TABLE history_pdf_access_requests
    RENAME COLUMN reviewed_by TO reviewed_by_employee_id;

UPDATE history_pdf_access_requests h
   SET reviewed_by_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE h.reviewed_by_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

ALTER TABLE history_pdf_access_requests
    ADD CONSTRAINT history_pdf_access_requests_reviewed_by_employee_fkey
    FOREIGN KEY (reviewed_by_employee_id) REFERENCES employees(id);

ALTER TABLE support_cases
    RENAME COLUMN assigned_user_id TO assigned_employee_id;

UPDATE support_cases s
   SET assigned_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE s.assigned_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

ALTER TABLE support_cases
    ADD CONSTRAINT support_cases_assigned_employee_fkey
    FOREIGN KEY (assigned_employee_id) REFERENCES employees(id);

ALTER TABLE support_interactions
    RENAME COLUMN actor_user_id TO actor_account_id;

ALTER TABLE support_interactions
    ADD COLUMN actor_account_type VARCHAR(20) NULL;

UPDATE support_interactions
   SET actor_account_type = CASE
       WHEN actor_account_id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        ) THEN 'EMPLOYEE'
       ELSE 'USER'
   END
 WHERE actor_account_id IS NOT NULL;

UPDATE support_interactions s
   SET actor_account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE s.actor_account_type = 'EMPLOYEE'
   AND s.actor_account_id = u.id;

ALTER TABLE support_interactions
    ADD CONSTRAINT support_interactions_actor_account_type_check
    CHECK (actor_account_type IS NULL OR actor_account_type IN ('EMPLOYEE', 'USER'));

ALTER TABLE support_notes
    RENAME COLUMN author_user_id TO author_employee_id;

UPDATE support_notes s
   SET author_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE s.author_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

ALTER TABLE support_notes
    ADD CONSTRAINT support_notes_author_employee_fkey
    FOREIGN KEY (author_employee_id) REFERENCES employees(id);

ALTER TABLE support_call_requests
    RENAME COLUMN reviewed_by_user_id TO reviewed_by_employee_id;

ALTER TABLE support_call_requests
    RENAME COLUMN assigned_user_id TO assigned_employee_id;

UPDATE support_call_requests r
   SET reviewed_by_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE r.reviewed_by_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

UPDATE support_call_requests r
   SET assigned_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE r.assigned_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

ALTER TABLE support_call_requests
    ADD CONSTRAINT support_call_requests_reviewed_by_employee_fkey
    FOREIGN KEY (reviewed_by_employee_id) REFERENCES employees(id);

ALTER TABLE support_call_requests
    ADD CONSTRAINT support_call_requests_assigned_employee_fkey
    FOREIGN KEY (assigned_employee_id) REFERENCES employees(id);

ALTER TABLE support_case_events
    RENAME COLUMN actor_user_id TO actor_account_id;

ALTER TABLE support_case_events
    ADD COLUMN actor_account_type VARCHAR(20) NULL;

UPDATE support_case_events
   SET actor_account_type = CASE
       WHEN actor_account_id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        ) THEN 'EMPLOYEE'
       ELSE 'USER'
   END
 WHERE actor_account_id IS NOT NULL;

UPDATE support_case_events s
   SET actor_account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE s.actor_account_type = 'EMPLOYEE'
   AND s.actor_account_id = u.id;

ALTER TABLE support_case_events
    ADD CONSTRAINT support_case_events_actor_account_type_check
    CHECK (actor_account_type IS NULL OR actor_account_type IN ('EMPLOYEE', 'USER'));

ALTER TABLE support_messages
    RENAME COLUMN sender_user_id TO sender_account_id;

UPDATE support_messages s
   SET sender_account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE s.sender_type = 'STAFF'
   AND s.sender_account_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

ALTER TABLE user_permission_overrides
    RENAME COLUMN granted_by_user_id TO granted_by_employee_id;

UPDATE user_permission_overrides o
   SET granted_by_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE o.granted_by_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

ALTER TABLE user_permission_overrides
    ADD CONSTRAINT user_permission_overrides_granted_by_employee_fkey
    FOREIGN KEY (granted_by_employee_id) REFERENCES employees(id);

ALTER TABLE voice_calls
    RENAME COLUMN caller_user_id TO caller_employee_id;

ALTER TABLE voice_calls
    RENAME COLUMN ended_by_user_id TO ended_by_account_id;

ALTER TABLE voice_calls
    ADD COLUMN ended_by_account_type VARCHAR(20) NULL;

UPDATE voice_calls v
   SET caller_employee_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE v.caller_employee_id = u.id
   AND u.id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

UPDATE voice_calls
   SET ended_by_account_type = CASE
       WHEN ended_by_account_id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        ) THEN 'EMPLOYEE'
       ELSE 'USER'
   END
 WHERE ended_by_account_id IS NOT NULL;

UPDATE voice_calls v
   SET ended_by_account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE v.ended_by_account_type = 'EMPLOYEE'
   AND v.ended_by_account_id = u.id;

ALTER TABLE voice_calls
    ADD CONSTRAINT voice_calls_caller_employee_fkey
    FOREIGN KEY (caller_employee_id) REFERENCES employees(id);

ALTER TABLE voice_calls
    ADD CONSTRAINT voice_calls_callee_user_fkey
    FOREIGN KEY (callee_user_id) REFERENCES users(id);

ALTER TABLE voice_calls
    ADD CONSTRAINT voice_calls_ended_by_account_type_check
    CHECK (
        ended_by_account_type IS NULL
        OR ended_by_account_type IN ('EMPLOYEE', 'USER')
    );

ALTER TABLE voice_call_participants
    DROP CONSTRAINT IF EXISTS uq_voice_call_participant_user;

ALTER TABLE voice_call_participants
    RENAME COLUMN user_id TO account_id;

ALTER TABLE voice_call_participants
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE voice_call_participants
   SET account_type = 'EMPLOYEE'
 WHERE account_id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

UPDATE voice_call_participants p
   SET account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE p.account_type = 'EMPLOYEE'
   AND p.account_id = u.id;

ALTER TABLE voice_call_participants
    ADD CONSTRAINT voice_call_participants_account_type_check
    CHECK (account_type IN ('EMPLOYEE', 'USER'));

ALTER TABLE voice_call_participants
    ADD CONSTRAINT uq_voice_call_participant_account
    UNIQUE (account_type, account_id);

ALTER TABLE rental_vendor_review_history
    RENAME COLUMN actor_user_id TO actor_account_id;

ALTER TABLE rental_vendor_review_history
    ADD COLUMN actor_account_type VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE rental_vendor_review_history
   SET actor_account_type = 'EMPLOYEE'
 WHERE actor_account_id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

UPDATE rental_vendor_review_history h
   SET actor_account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE h.actor_account_type = 'EMPLOYEE'
   AND h.actor_account_id = u.id;

ALTER TABLE rental_vendor_review_history
    ADD CONSTRAINT rental_vendor_review_history_actor_type_check
    CHECK (actor_account_type IN ('EMPLOYEE', 'USER'));

ALTER TABLE rental_car_review_history
    RENAME COLUMN actor_user_id TO actor_account_id;

ALTER TABLE rental_car_review_history
    ADD COLUMN actor_account_type VARCHAR(20) NOT NULL DEFAULT 'USER';

UPDATE rental_car_review_history
   SET actor_account_type = 'EMPLOYEE'
 WHERE actor_account_id IN (
            SELECT id FROM users
            WHERE mobile IN ('9999999999', '9999999998')
        );

UPDATE rental_car_review_history h
   SET actor_account_id = e.id
  FROM users u
  JOIN employees e ON e.mobile = u.mobile
 WHERE h.actor_account_type = 'EMPLOYEE'
   AND h.actor_account_id = u.id;

ALTER TABLE rental_car_review_history
    ADD CONSTRAINT rental_car_review_history_actor_type_check
    CHECK (actor_account_type IN ('EMPLOYEE', 'USER'));

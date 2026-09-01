-- Hibernate 7 validates column types strictly; status_code was created as SMALLINT
-- but the IdempotencyKey entity maps it as Integer (INT). All HTTP status codes fit
-- in INT, so this is a no-data-loss change.
ALTER TABLE idempotency_key MODIFY COLUMN status_code INT NOT NULL;

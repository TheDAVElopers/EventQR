UPDATE scan_purposes
SET tracking_only = true,
    description = 'Verify attendee registration without ID printing.'
WHERE lower(name) = 'verification'
  AND code = 'REGISTRATION_LOOKUP';

UPDATE transaction_rules
SET points_awarded = 0,
    updated_at = now()
WHERE scan_purpose_id IN (
    SELECT id
    FROM scan_purposes
    WHERE lower(name) = 'verification'
      AND code = 'REGISTRATION_LOOKUP'
);

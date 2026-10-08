-- V7 forced max_uses_per_registration/duplicate_window_minutes to fixed values, discarding custom limits.
-- New semantics:
--   allow_duplicate = false -> single use (max 1, window 0)
--   allow_duplicate = true  -> keep supplied values; max_uses 0 = unlimited (NULL/negative -> 0),
--                              duplicate_window_minutes NULL/negative -> 0
-- The trigger from V7 is kept and re-created idempotently.
CREATE OR REPLACE FUNCTION normalize_transaction_rule_duplicate_settings()
RETURNS trigger AS $$
BEGIN
    IF COALESCE(NEW.allow_duplicate, false) THEN
        NEW.max_uses_per_registration := GREATEST(COALESCE(NEW.max_uses_per_registration, 0), 0);
        NEW.duplicate_window_minutes := GREATEST(COALESCE(NEW.duplicate_window_minutes, 0), 0);
    ELSE
        NEW.max_uses_per_registration := 1;
        NEW.duplicate_window_minutes := 0;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS normalize_transaction_rule_duplicate_settings ON transaction_rules;

CREATE TRIGGER normalize_transaction_rule_duplicate_settings
BEFORE INSERT OR UPDATE OF allow_duplicate, max_uses_per_registration, duplicate_window_minutes
ON transaction_rules
FOR EACH ROW
EXECUTE FUNCTION normalize_transaction_rule_duplicate_settings();

CREATE OR REPLACE FUNCTION normalize_transaction_rule_duplicate_settings()
RETURNS trigger AS $$
BEGIN
    IF COALESCE(NEW.allow_duplicate, false) THEN
        NEW.max_uses_per_registration := 0;
        NEW.duplicate_window_minutes := 0;
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

UPDATE transaction_rules
SET allow_duplicate = allow_duplicate;
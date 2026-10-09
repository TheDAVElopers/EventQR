-- Password reset moved from an emailed link to an emailed 6-digit code. Codes are brute-forceable, so each row
-- counts wrong guesses and is locked (used = true) after 5. Outstanding link-style tokens are incompatible with
-- the code lookup, so retire them; affected users simply request a new code.
ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS failed_attempts integer NOT NULL DEFAULT 0;

UPDATE password_reset_tokens
SET used = true
WHERE used = false;

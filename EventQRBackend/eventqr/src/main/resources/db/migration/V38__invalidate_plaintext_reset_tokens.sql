-- Password reset tokens are now stored as their SHA-256 hash (PasswordResetService). Rows written before that
-- change hold the raw token, which the hashed lookup can never match, and a raw secret should not stay at rest.
-- Retire every outstanding one; affected users simply request a new reset link (links lived 30 minutes anyway).
UPDATE password_reset_tokens
SET used = true
WHERE used = false;

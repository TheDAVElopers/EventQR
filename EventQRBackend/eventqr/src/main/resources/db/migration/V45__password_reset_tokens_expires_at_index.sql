-- V45__password_reset_tokens_expires_at_index.sql
-- Missing expires_at index for the password-reset purge.
--
-- The hourly sweep PasswordResetService.purgeExpiredTokens deletes every row whose expires_at has passed, but
-- password_reset_tokens had no index on expires_at, so that bulk DELETE was a sequential scan. Its two sibling
-- purges are already indexed -- idx_revoked_token_expires_at (V27) and idx_refresh_token_expires_at (V29) -- so
-- this adds the third, same name pattern and same shape.
--
-- Plain CREATE INDEX (not CONCURRENTLY): the table is tiny in production (0 rows, 72 kB) so the build is
-- instantaneous and Flyway can run it inside its own transaction. CONCURRENTLY cannot run in a transaction and
-- would need extra Flyway configuration, so it is not worth it here.
--
-- Idempotent: IF NOT EXISTS, so a re-run (or a database where the index was added by hand) is a no-op.

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at ON public.password_reset_tokens (expires_at);

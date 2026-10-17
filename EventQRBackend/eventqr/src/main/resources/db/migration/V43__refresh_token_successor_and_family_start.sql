-- V43__refresh_token_successor_and_family_start.sql
-- Two hardening columns for refresh-token rotation (RefreshTokenService.rotate).
--
-- 1. replaced_by_hash: the token_hash of the successor issued when this token was rotated. Today a retry inside
--    the grace window (configurable, default 60 s) issues a second successor while the first one stays valid, so a single used token can
--    fork its family into two live branches. With this column the backend can revoke the earlier successor (looked
--    up by token_hash through ux_refresh_token_hash) before it issues the new one. Same type as token_hash
--    (varchar(64), SHA-256 hex). Nullable: NULL means the token was never rotated. Every existing row stays NULL.
--    There is no index: the backend only follows the link forward (row -> successor), and that lookup already uses
--    the unique index on token_hash. There is no FK to token_hash: the hourly purge deletes expired rows, and an FK
--    would need its own index and ON DELETE handling for no gain. The CHECK only rules out a row naming itself.
--
-- 2. family_created_at: when the login that started this family happened. The backend copies it unchanged to each
--    successor and refuses to rotate once the family is older than the absolute session lifetime. Backfill: the
--    earliest created_at in the same family_id. Checked against production before writing (read-only): 179 rows,
--    144 families, oldest row 2026-10-06, 0 expired rows, a single lifetime (expires_at - created_at) across all
--    rows. With a 30-day lifetime, nothing has been purged yet, so the earliest surviving row is the real start of
--    its family and the backfill is exact. (In an environment where purge already deleted a family's first rows,
--    the backfill is the earliest surviving row. That date is later than the real start, so the backfill errs
--    toward a longer session, never a shorter one.)
--    The column is added nullable with no default, then backfilled, then given DEFAULT now() and NOT NULL. Adding it
--    with DEFAULT now() first would stamp every existing row with the migration time. The default only covers an
--    insert path that does not set the column. The backend must set it explicitly: the issue time for a new login,
--    and the predecessor's value on rotation.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS, the backfill only touches NULL rows, SET DEFAULT/SET NOT NULL can be repeated,
-- and the CHECK is dropped and re-added.
-- Locking: every statement runs in Flyway's single transaction, and the first ALTER takes ACCESS EXCLUSIVE until
-- commit. The table is tiny (179 rows, 160 kB), so the backfill and the NOT NULL/CHECK scans take milliseconds.
-- lock_timeout makes the migration fail fast (and roll back cleanly) if a long transaction holds the table, instead
-- of queueing every refresh behind it. Rotations use short SELECT ... FOR UPDATE transactions, so this should not
-- trigger. Plain PostgreSQL (no Supabase-specific objects referenced). RLS (enabled in V29, no policies) is unchanged.

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.refresh_token
    ADD COLUMN IF NOT EXISTS replaced_by_hash  varchar(64) NULL,
    ADD COLUMN IF NOT EXISTS family_created_at timestamptz NULL;

UPDATE public.refresh_token t
SET family_created_at = f.started_at
FROM (
    SELECT family_id, min(created_at) AS started_at
    FROM public.refresh_token
    GROUP BY family_id
) f
WHERE t.family_id = f.family_id
  AND t.family_created_at IS NULL;

ALTER TABLE public.refresh_token
    ALTER COLUMN family_created_at SET DEFAULT now(),
    ALTER COLUMN family_created_at SET NOT NULL;

ALTER TABLE public.refresh_token
    DROP CONSTRAINT IF EXISTS chk_refresh_token_replaced_by_not_self;
ALTER TABLE public.refresh_token
    ADD CONSTRAINT chk_refresh_token_replaced_by_not_self
        CHECK (replaced_by_hash IS NULL OR replaced_by_hash <> token_hash);

-- Rollback (manual; Flyway Community has no undo). Run only after the backend no longer maps these columns, because
-- ddl-auto=validate fails at startup if the entity maps a column that is gone:
--   ALTER TABLE public.refresh_token DROP CONSTRAINT IF EXISTS chk_refresh_token_replaced_by_not_self;
--   ALTER TABLE public.refresh_token DROP COLUMN IF EXISTS replaced_by_hash, DROP COLUMN IF EXISTS family_created_at;
--   DELETE FROM flyway_schema_history WHERE version = '43';
-- Nothing else depends on these columns, so dropping them loses only the successor links and family start times.

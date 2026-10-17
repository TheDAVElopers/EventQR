-- Two-phase ID printing. A print request now inserts an id_print_logs row with status = 'PENDING' (the backend
-- sets it explicitly). A confirm endpoint later moves it to PRINTED, CANCELLED or FAILED and stamps confirmed_at.
-- Only PRINTED rows count as "this attendee has a printed ID".
--
-- Existing rows: every log written before this migration came from the old one-shot flow, which logged the print
-- as done right away. Those rows become PRINTED. The column DEFAULT fills them when the column is added (PG 11+
-- stores a constant default as metadata, so there is no table rewrite). The UPDATE below repeats that backfill
-- explicitly so the result does not depend on how the column was first created (for example, a column that
-- already existed without the default). confirmed_at stays NULL on legacy rows because they were never confirmed
-- through the new endpoint. Their print time is still in printed_at.
--
-- The DEFAULT stays 'PRINTED' on purpose, so an insert path that predates this change (or a manual insert) keeps
-- the old meaning. The new code must pass 'PENDING' explicitly.
--
-- Index: the per-attendee lookup was not covered. The existing indexes are on (event_id) and (registration_id)
-- only. The backend's check (IdPrintLogRepository.existsEffectivelyPrinted) is
--   event_id = ? AND attendee_user_id = ? AND (status = 'PRINTED' OR (status = 'PENDING' AND created_at < ?))
-- A partial index WHERE status = 'PRINTED' cannot serve that OR, so this is a plain composite index. The
-- (event_id, attendee_user_id) prefix narrows the search to a handful of rows per attendee, and status/created_at
-- let the OR be checked from the index alone. It also serves "latest PRINTED log for an attendee" queries.
--
-- Idempotent: ADD COLUMN IF NOT EXISTS, constraint dropped and re-added, index IF NOT EXISTS.
-- Locking: ALTER TABLE takes ACCESS EXCLUSIVE briefly. Adding the CHECK scans the table under that lock, and the
-- plain index build holds SHARE. The table is tiny (10 rows, 64 kB in production), so all of this is milliseconds.
-- Plain PostgreSQL (no Supabase-specific objects referenced).

-- printed_by_user_id: an old ddl-auto=update created this column (and its FK) in production, but no migration
-- creates it, so a fresh database failed ddl-auto=validate. On production both statements below are no-ops: the
-- column exists, and fk_id_print_logs_printed_by already exists, is validated, and was checked before writing this
-- (0 non-null values, 0 orphans). On a fresh database the column is empty, so the FK always validates.
-- The FK name and the guard follow the V18 pattern.
ALTER TABLE public.id_print_logs
    ADD COLUMN IF NOT EXISTS printed_by_user_id uuid NULL;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint
                 WHERE conname = 'fk_id_print_logs_printed_by' AND connamespace = 'public'::regnamespace) THEN
    ALTER TABLE public.id_print_logs
        ADD CONSTRAINT fk_id_print_logs_printed_by
        FOREIGN KEY (printed_by_user_id) REFERENCES public.user_profiles(id)
        ON DELETE SET NULL;
  END IF;
END $$;

ALTER TABLE public.id_print_logs
    ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT 'PRINTED',
    ADD COLUMN IF NOT EXISTS confirmed_at timestamptz NULL;

UPDATE public.id_print_logs
SET status = 'PRINTED'
WHERE status IS NULL
   OR status NOT IN ('PENDING', 'PRINTED', 'CANCELLED', 'FAILED');

ALTER TABLE public.id_print_logs
    DROP CONSTRAINT IF EXISTS chk_id_print_logs_status;
ALTER TABLE public.id_print_logs
    ADD CONSTRAINT chk_id_print_logs_status
        CHECK (status IN ('PENDING', 'PRINTED', 'CANCELLED', 'FAILED'));

CREATE INDEX IF NOT EXISTS idx_id_print_logs_event_attendee_status
    ON public.id_print_logs (event_id, attendee_user_id, status, created_at);

-- Rollback (manual; Flyway Community has no undo). Run only after the backend no longer reads these columns,
-- because ddl-auto=validate fails at startup if the entity maps a column that is gone:
--   DROP INDEX IF EXISTS public.idx_id_print_logs_event_attendee_status;
--   (printed_by_user_id and fk_id_print_logs_printed_by predate V42 in production; do not drop them there.)
--   ALTER TABLE public.id_print_logs DROP CONSTRAINT IF EXISTS chk_id_print_logs_status;
--   ALTER TABLE public.id_print_logs DROP COLUMN IF EXISTS confirmed_at, DROP COLUMN IF EXISTS status;
--   DELETE FROM flyway_schema_history WHERE version = '42';
-- Warning: dropping status loses which rows were PENDING/CANCELLED/FAILED. Rows that are not PRINTED would then read
-- as real prints under the old schema (success stays true). Before rolling back, consider
--   DELETE FROM public.id_print_logs WHERE status <> 'PRINTED';

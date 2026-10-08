-- The registration trigger (handle_event_registration_qr, V4/V5) inserts into qr_credentials with
-- ON CONFLICT (registration_id), which requires a unique constraint on that column. The baseline
-- (V16) never created it, so on a database built from these migrations every registration insert
-- failed with "there is no unique or exclusion constraint matching the ON CONFLICT specification".
-- Environments that predate the baseline already have both constraints under these exact names, so
-- each one is added only when missing and this migration is a no-op there.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'qr_credentials_registration_uniq'
                     AND conrelid = 'public.qr_credentials'::regclass) THEN
        ALTER TABLE public.qr_credentials
            ADD CONSTRAINT qr_credentials_registration_uniq UNIQUE (registration_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'qr_credentials_qr_value_uniq'
                     AND conrelid = 'public.qr_credentials'::regclass) THEN
        ALTER TABLE public.qr_credentials
            ADD CONSTRAINT qr_credentials_qr_value_uniq UNIQUE (qr_value);
    END IF;
END
$$;

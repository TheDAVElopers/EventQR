-- QR credential values are bearer secrets, so they must be unguessable.
--
-- Before (V4/V5): 'EVTQR-' || event_id || '-' || attendee_user_id || '-' || registration_id. All three ids are
-- exposed through the API or the JWT, so anyone who knew them could forge another attendee's credential.
-- After: the format QrValueGenerator.generate() already uses, 'EVQR-' plus 32 uppercase hex characters from a random
-- UUID (122 random bits). Core gen_random_uuid() is built into pg_catalog since PostgreSQL 13 and backed by
-- pg_strong_random() (a CSPRNG). It needs no pgcrypto or `extensions` schema, so this runs unchanged on Supabase
-- and plain local Postgres. The value is 37 characters, which fits varchar(120), and uppercase hex keeps the
-- case-insensitive lookup (findByQrValueIgnoreCase) unambiguous. It contains non-digits, so the scanner's
-- all-digits short-id heuristic never mistakes it for a registration number.
--
-- Hardening: the function is SECURITY DEFINER, so it pins search_path to '' (empty) and schema-qualifies every
-- table, function and type it references. pg_catalog is still searched implicitly (first), so the remaining
-- unqualifiable pieces (operators such as ||, COALESCE) resolve only to built-ins and cannot be shadowed through
-- a writable schema. V5/V37 pinned it to `public`; '' is stricter and the body no longer needs any search path.
--
-- Idempotent: CREATE OR REPLACE keeps the function's OID, owner, EXECUTE grants and the existing
-- trg_event_registration_qr binding. The reissue UPDATE only matches legacy values, so re-running it changes nothing.

CREATE OR REPLACE FUNCTION public.handle_event_registration_qr()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  new_qr_id pg_catalog.uuid;
  new_qr_value pg_catalog.text;
BEGIN
  -- Keep in lockstep with QrValueGenerator.generate().
  new_qr_value := 'EVQR-' || pg_catalog.upper(pg_catalog.replace(pg_catalog.gen_random_uuid()::pg_catalog.text, '-', ''));

  INSERT INTO public.qr_credentials (
    qr_value,
    event_id,
    attendee_user_id,
    registration_id,
    display_status,
    delivery_status,
    active,
    downloaded
  ) VALUES (
    new_qr_value,
    new.event_id,
    new.attendee_user_id,
    new.id,
    'PENDING',
    'PENDING',
    true,
    false
  )
  ON CONFLICT (registration_id) DO UPDATE
  SET active = true,
      updated_at = pg_catalog.now()
  RETURNING id INTO new_qr_id;

  UPDATE public.event_registrations
  SET qr_credential_id = new_qr_id,
      registered_at = coalesce(registered_at, pg_catalog.now()),
      updated_at = pg_catalog.now()
  WHERE id = new.id
    AND qr_credential_id IS NULL;

  RETURN new;
END;
$$;

-- Reissue every legacy, derivable value in place. Credential ids do not change, so the FKs from transaction_logs,
-- event_registrations and id_print_logs stay valid. gen_random_uuid() is volatile, so it runs once per row and every
-- row gets its own value. qr_credentials_qr_value_uniq (V31) rejects the (negligible) chance of a collision. If one
-- happens, the migration transaction rolls back and can simply be re-run.
-- display_status goes back to PENDING so the new code can be shown again, but REVOKED rows stay revoked.
-- delivery_status and active are left alone: nothing is re-sent automatically, and inactive credentials stay inactive.
UPDATE public.qr_credentials
SET qr_value       = 'EVQR-' || upper(replace(pg_catalog.gen_random_uuid()::text, '-', '')),
    display_status = CASE WHEN display_status = 'REVOKED' THEN display_status ELSE 'PENDING' END,
    downloaded     = false,
    updated_at     = now()
WHERE qr_value ILIKE 'EVTQR-%';

-- Rollback (manual; Flyway Community has no undo). Restore the V5 function body. The reissued values cannot and
-- should not be restored, because the old values were derivable.
--   CREATE OR REPLACE FUNCTION public.handle_event_registration_qr() ... (body from V5__fix_event_registration_qr_trigger.sql)
--   DELETE FROM flyway_schema_history WHERE version = '40';

-- The application owns events.current_attendee_count (atomic increment on register, decrement on cancel, see
-- EventRepository). A legacy INSERT trigger on event_registrations bumped it as well, so every registration
-- counted twice: attendee screens showed double the real number and the capacity guard refused registrations
-- at half the real capacity. Drop it and recompute the counters from the registrations.
-- Idempotent: environments built from the baseline never had the trigger.
DROP TRIGGER IF EXISTS trg_event_registration_counts_insert ON public.event_registrations;

-- Rows that are deleted (e.g. an account cascade) must free their seat, but a CANCELLED row already freed it
-- when it was cancelled, so it must not be subtracted a second time.
CREATE OR REPLACE FUNCTION public.handle_event_registration_counts()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
begin
  if tg_op = 'DELETE' and old.status <> 'CANCELLED' then
    update public.events
    set current_attendee_count = greatest(current_attendee_count - 1, 0),
        updated_at = now()
    where id = old.event_id;
  end if;
  return old;
end;
$function$;

DROP TRIGGER IF EXISTS trg_event_registration_counts_delete ON public.event_registrations;
CREATE TRIGGER trg_event_registration_counts_delete
AFTER DELETE ON public.event_registrations
FOR EACH ROW EXECUTE FUNCTION public.handle_event_registration_counts();

UPDATE public.events e
SET current_attendee_count = COALESCE(
        (SELECT COUNT(*) FROM public.event_registrations r WHERE r.event_id = e.id AND r.status <> 'CANCELLED'), 0)
WHERE e.current_attendee_count IS DISTINCT FROM COALESCE(
        (SELECT COUNT(*) FROM public.event_registrations r WHERE r.event_id = e.id AND r.status <> 'CANCELLED'), 0);

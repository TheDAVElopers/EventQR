-- "Registered" now means status NOT IN ('CANCELLED', 'NO_SHOW') everywhere (RegistrationStatus.isCountedAsRegistered):
-- organizer screens, dashboard and the capacity guard. events.current_attendee_count previously excluded only
-- CANCELLED, so NO_SHOW seats were still counted against capacity. The application now releases the seat when an
-- organizer marks a registration NO_SHOW (and re-takes it when moved back), so recompute the counter to match.
UPDATE public.events e
SET current_attendee_count = COALESCE(
        (SELECT COUNT(*) FROM public.event_registrations r
          WHERE r.event_id = e.id AND r.status NOT IN ('CANCELLED', 'NO_SHOW')), 0)
WHERE e.current_attendee_count IS DISTINCT FROM COALESCE(
        (SELECT COUNT(*) FROM public.event_registrations r
          WHERE r.event_id = e.id AND r.status NOT IN ('CANCELLED', 'NO_SHOW')), 0);

-- Deleting a registration must free its seat only if it was still counted.
CREATE OR REPLACE FUNCTION public.handle_event_registration_counts()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
begin
  if tg_op = 'DELETE' and old.status NOT IN ('CANCELLED', 'NO_SHOW') then
    update public.events
    set current_attendee_count = greatest(current_attendee_count - 1, 0),
        updated_at = now()
    where id = old.event_id;
  end if;
  return old;
end;
$function$;

drop trigger if exists trg_event_registration_qr on public.event_registrations;

create or replace function public.handle_event_registration_qr()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  new_qr_id uuid;
  new_qr_value text;
begin
  if new.qr_credential_id is null then
    new_qr_value := 'EVTQR-' || new.event_id::text || '-' || new.attendee_user_id::text || '-' || new.id::text;

    insert into public.qr_credentials (
      qr_value,
      event_id,
      attendee_user_id,
      registration_id,
      display_status,
      delivery_status,
      active,
      downloaded
    ) values (
      new_qr_value,
      new.event_id,
      new.attendee_user_id,
      new.id,
      'PENDING',
      'PENDING',
      true,
      false
    )
    on conflict (registration_id) do update
    set active = true,
        updated_at = now()
    returning id into new_qr_id;

    update public.event_registrations
    set qr_credential_id = new_qr_id,
        registered_at = coalesce(registered_at, now()),
        updated_at = now()
    where id = new.id
      and qr_credential_id is null;
  end if;

  return new;
end;
$$;

create trigger trg_event_registration_qr
after insert on public.event_registrations
for each row
execute function public.handle_event_registration_qr();
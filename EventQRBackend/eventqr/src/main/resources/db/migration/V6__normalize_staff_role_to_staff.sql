alter table if exists event_staff_assignments
    add column if not exists staff_role varchar(64);

alter table if exists event_staff_assignments
    drop constraint if exists event_staff_assignments_staff_role_check;

alter table if exists event_staff_assignments
    drop constraint if exists chk_event_staff_assignments_staff_role;

do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select con.conname
        from pg_constraint con
                 join pg_class rel on rel.oid = con.conrelid
                 join pg_namespace nsp on nsp.oid = rel.relnamespace
        where rel.relname = 'event_staff_assignments'
          and nsp.nspname = current_schema()
          and con.contype = 'c'
          and pg_get_constraintdef(con.oid) ilike '%staff_role%'
        loop
            execute format('alter table %I.%I drop constraint if exists %I',
                           current_schema(), 'event_staff_assignments', constraint_name);
        end loop;
end $$;

update event_staff_assignments
set staff_role = 'STAFF'
where staff_role is null
   or btrim(staff_role) = ''
   or upper(staff_role) <> 'STAFF';

update event_staff_assignments
set role_label = 'Staff'
where role_label is null
   or btrim(role_label) = ''
   or upper(role_label) in ('SCANNER', 'REGISTRATION_STAFF', 'ID_PRINTER', 'REWARD_STAFF', 'EVENT_MANAGER', 'STAFF');

alter table if exists event_staff_assignments
    alter column staff_role set default 'STAFF';

alter table if exists event_staff_assignments
    alter column staff_role set not null;

alter table if exists event_staff_assignments
    alter column role_label set default 'Staff';

alter table if exists event_staff_assignments
    add constraint chk_event_staff_assignments_staff_role
        check (staff_role = 'STAFF');

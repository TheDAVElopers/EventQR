create table if not exists event_staff_assignments (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    event_id uuid not null,
    staff_user_id uuid not null,
    role_label varchar(120) not null default 'Scanner',
    active boolean not null default true,
    permissions text,
    added_by_user_id uuid,
    added_at timestamptz not null default now(),
    constraint uq_event_staff_assignment unique (event_id, staff_user_id)
);

create index if not exists idx_event_staff_assignments_event_id on event_staff_assignments(event_id);
create index if not exists idx_event_staff_assignments_staff_user_id on event_staff_assignments(staff_user_id);
create index if not exists idx_events_organizer_status on events(organizer_user_id, status);
create index if not exists idx_event_registrations_event_status on event_registrations(event_id, status);
create index if not exists idx_transaction_logs_event_result_type on transaction_logs(event_id, transaction_result, transaction_type);
create index if not exists idx_transaction_logs_staff_user_id on transaction_logs(staff_user_id);
create index if not exists idx_scan_purposes_event_code on scan_purposes(event_id, code);
create index if not exists idx_transaction_rules_event_scan_purpose on transaction_rules(event_id, scan_purpose_id);

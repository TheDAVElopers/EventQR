create table if not exists event_requests (
    id uuid primary key,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    requester_user_id uuid not null,
    event_name varchar(255) not null,
    event_description varchar(3000) not null,
    event_category varchar(255) not null,
    target_audience varchar(255),
    capacity integer not null,
    venue varchar(255) not null,
    start_date_time timestamptz not null,
    end_date_time timestamptz not null,
    registration_start_date_time timestamptz,
    registration_end_date_time timestamptz,
    requester_name varchar(255) not null,
    contact_email varchar(255) not null,
    contact_number varchar(255) not null,
    requested_features jsonb,
    event_logo_url varchar(255),
    additional_notes varchar(3000),
    reason_for_request varchar(3000) not null,
    status varchar(50) not null,
    admin_remarks varchar(2000),
    reviewed_by_user_id uuid,
    reviewed_at timestamptz
);

create index if not exists idx_event_requests_requester_user_id
    on event_requests(requester_user_id);

create index if not exists idx_event_requests_status
    on event_requests(status);

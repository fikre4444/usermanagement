-- Core schema of the user management service.

create table roles (
    id          uuid         primary key,
    name        varchar(50)  not null unique,
    description varchar(255),
    system_role boolean      not null default false,
    version     bigint       not null default 0,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

create table role_permissions (
    role_id    uuid         not null references roles (id) on delete cascade,
    permission varchar(100) not null,
    primary key (role_id, permission)
);

create table users (
    id                    uuid         primary key,
    user_type             varchar(50)  not null,
    username              varchar(50)  unique,
    email                 varchar(254) unique,
    phone                 varchar(20)  unique,
    password_hash         varchar(255),
    first_name            varchar(100),
    last_name             varchar(100),
    status                varchar(30)  not null,
    email_verified        boolean      not null default false,
    phone_verified        boolean      not null default false,
    failed_login_attempts integer      not null default 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    password_changed_at   timestamptz,
    deleted_at            timestamptz,
    attributes            jsonb        not null default '{}'::jsonb,
    version               bigint       not null default 0,
    created_at            timestamptz  not null,
    updated_at            timestamptz  not null
);

create index idx_users_user_type on users (user_type);
create index idx_users_status on users (status);
create index idx_users_created_at on users (created_at);
create index idx_users_attributes on users using gin (attributes);

create table user_roles (
    user_id uuid not null references users (id) on delete cascade,
    role_id uuid not null references roles (id),
    primary key (user_id, role_id)
);

create index idx_user_roles_role on user_roles (role_id);

create table refresh_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    token_hash varchar(64) not null unique,
    family_id  uuid        not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    version    bigint      not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);
create index idx_refresh_tokens_family on refresh_tokens (family_id);
create index idx_refresh_tokens_expires on refresh_tokens (expires_at);

create table otp_codes (
    id          uuid        primary key,
    subject_id  uuid        not null,
    purpose     varchar(30) not null,
    channel     varchar(10) not null,
    code_hash   varchar(64) not null,
    expires_at  timestamptz not null,
    attempts    integer     not null default 0,
    consumed_at timestamptz,
    version     bigint      not null default 0,
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);

create index idx_otp_codes_subject on otp_codes (subject_id, purpose, created_at desc);
create index idx_otp_codes_expires on otp_codes (expires_at);

create table outbox_events (
    id              uuid         primary key,
    aggregate_type  varchar(50)  not null,
    aggregate_id    varchar(100) not null,
    event_type      varchar(100) not null,
    payload         jsonb        not null,
    occurred_at     timestamptz  not null,
    published_at    timestamptz,
    failed_at       timestamptz,
    attempts        integer      not null default 0,
    next_attempt_at timestamptz  not null,
    last_error      text
);

create index idx_outbox_pending on outbox_events (next_attempt_at)
    where published_at is null and failed_at is null;
create index idx_outbox_published on outbox_events (published_at);

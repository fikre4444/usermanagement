-- The outbox now carries several streams: business events and the activity (audit) log.
alter table outbox_events add column stream varchar(50) not null default 'domain-events';
alter table outbox_events alter column stream drop default;

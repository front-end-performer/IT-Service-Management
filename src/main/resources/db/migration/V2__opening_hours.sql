-- Opening hours per provider in its local time zone (wall-clock times, one period per weekday).
-- A provider without rows is open around the clock.

alter table provider add column time_zone varchar(64) not null default 'UTC';

create table provider_opening_hours (
    provider_id varchar(64) not null references provider (id),
    day_of_week varchar(9)  not null,
    opens_at    time        not null,
    closes_at   time        not null,
    constraint pk_provider_opening_hours primary key (provider_id, day_of_week),
    constraint ck_opening_hours_period check (closes_at > opens_at)
);

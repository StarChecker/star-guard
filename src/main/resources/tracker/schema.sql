create table if not exists tracked_repo (
    id bigserial primary key,
    node_id text not null unique,
    full_name text not null,
    full_name_lc text not null,
    repo_created_at timestamptz,
    stars integer not null default 0,
    forks integer not null default 0,
    popular_rank integer,
    watched_until timestamptz,
    tracked_since timestamptz not null,
    last_polled_at timestamptz
);

create index if not exists tracked_repo_name_idx on tracked_repo (full_name_lc);
create index if not exists tracked_repo_popular_idx on tracked_repo (popular_rank);
create index if not exists tracked_repo_watched_idx on tracked_repo (watched_until);

create table if not exists star_sample (
    repo_id bigint not null references tracked_repo (id) on delete cascade,
    at timestamptz not null,
    stars integer not null,
    forks integer not null,
    primary key (repo_id, at)
);

create table if not exists star_hourly (
    repo_id bigint not null references tracked_repo (id) on delete cascade,
    hour_at timestamptz not null,
    stars integer not null,
    forks integer not null,
    primary key (repo_id, hour_at)
);

create index if not exists star_hourly_hour_idx on star_hourly (hour_at);

create table if not exists poll_run (
    at timestamptz primary key,
    repos integer not null,
    failed integer not null,
    duration_ms integer not null,
    rate_remaining integer
);

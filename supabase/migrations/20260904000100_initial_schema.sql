-- Winter Arc — initial schema
--
-- Design notes
--
-- 1. EVERY row is owned by a user and every table has row-level security enabled with
--    policies scoped to auth.uid(). There is no path by which one account reads another's
--    training data.
--
-- 2. PRIMARY KEYS ARE CLIENT-GENERATED UUIDs. The Android app is offline-first: rows are
--    created on the device, often with no connectivity, and pushed later. Client-generated
--    ids mean an offline insert can never collide with a server-assigned sequence.
--
-- 3. SYNC METADATA. Each row carries updated_at and deleted_at. Deletes are soft so that a
--    delete performed offline can still be propagated, and so a row deleted on one device
--    is not silently resurrected by another device that still holds it.
--
-- 4. HISTORY IS APPEND-MOSTLY. Completed sessions are not meant to change. The schema does
--    not physically forbid an update — the user owns their data and may correct a typo —
--    but the application layer freezes completed sessions, and sync uses updated_at so a
--    correction propagates rather than being lost.

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------------------
-- Profile
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id            uuid primary key references auth.users(id) on delete cascade,
    display_name  text,
    height_cm     numeric(6,2),
    weight_unit   text not null default 'KG' check (weight_unit in ('KG','LB')),
    length_unit   text not null default 'CM' check (length_unit in ('CM','IN')),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Exercise library
-- ---------------------------------------------------------------------------
create table if not exists public.exercises (
    id                   uuid primary key,
    user_id              uuid not null references auth.users(id) on delete cascade,
    name                 text not null,
    primary_muscle       text not null,
    equipment            text not null,
    default_rest_seconds integer not null default 90 check (default_rest_seconds >= 0),
    notes                text,
    is_custom            boolean not null default true,
    is_archived          boolean not null default false,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    deleted_at           timestamptz
);
create index if not exists exercises_user_idx on public.exercises(user_id) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- Programme / templates (the PLAN)
-- ---------------------------------------------------------------------------
create table if not exists public.programmes (
    id          uuid primary key,
    user_id     uuid not null references auth.users(id) on delete cascade,
    name        text not null,
    description text,
    is_active   boolean not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz
);
create index if not exists programmes_user_idx on public.programmes(user_id) where deleted_at is null;

create table if not exists public.workout_templates (
    id            uuid primary key,
    user_id       uuid not null references auth.users(id) on delete cascade,
    programme_id  uuid not null references public.programmes(id) on delete cascade,
    name          text not null,
    day_of_week   integer check (day_of_week between 1 and 7),
    day_type      text not null default 'TRAINING',
    muscle_groups text[] not null default '{}',
    position      integer not null default 0,
    notes         text,
    is_archived   boolean not null default false,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    deleted_at    timestamptz
);
create index if not exists templates_programme_idx on public.workout_templates(programme_id) where deleted_at is null;

create table if not exists public.planned_exercises (
    id               uuid primary key,
    user_id          uuid not null references auth.users(id) on delete cascade,
    template_id      uuid not null references public.workout_templates(id) on delete cascade,
    exercise_id      uuid not null references public.exercises(id) on delete cascade,
    position         integer not null default 0,
    superset_group   text,
    sets             integer not null check (sets > 0),
    rep_low          integer not null check (rep_low > 0),
    rep_high         integer not null check (rep_high >= rep_low),
    target_weight_kg numeric(7,2) check (target_weight_kg >= 0),
    rir              text,
    rest_seconds     integer not null default 90 check (rest_seconds >= 0),
    style            text not null default 'UNSPECIFIED',
    notes            text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    deleted_at       timestamptz
);
create index if not exists planned_template_idx on public.planned_exercises(template_id) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- Sessions (the ACTUAL) — permanent history
-- ---------------------------------------------------------------------------
create table if not exists public.workout_sessions (
    id           uuid primary key,
    user_id      uuid not null references auth.users(id) on delete cascade,
    template_id  uuid references public.workout_templates(id) on delete set null,
    programme_id uuid references public.programmes(id) on delete set null,
    name         text not null,
    session_date date not null,
    started_at   timestamptz not null,
    finished_at  timestamptz,
    status       text not null default 'IN_PROGRESS'
                 check (status in ('IN_PROGRESS','COMPLETED','ABANDONED')),
    day_type     text not null default 'TRAINING',
    notes        text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz
);
create index if not exists sessions_user_date_idx
    on public.workout_sessions(user_id, session_date desc) where deleted_at is null;

-- The plan is SNAPSHOTTED into these columns when the session starts. Editing a template
-- afterwards cannot reach back and rewrite what a past session prescribed.
create table if not exists public.performed_exercises (
    id                    uuid primary key,
    user_id               uuid not null references auth.users(id) on delete cascade,
    session_id            uuid not null references public.workout_sessions(id) on delete cascade,
    exercise_id           uuid not null references public.exercises(id) on delete cascade,
    position              integer not null default 0,
    superset_group        text,
    planned_sets          integer not null default 0 check (planned_sets >= 0),
    planned_rep_low       integer not null default 0,
    planned_rep_high      integer not null default 0,
    planned_weight_kg     numeric(7,2) check (planned_weight_kg >= 0),
    planned_rest_seconds  integer not null default 90,
    planned_style         text not null default 'UNSPECIFIED',
    replaced_exercise_id  uuid references public.exercises(id) on delete set null,
    is_ad_hoc             boolean not null default false,
    is_skipped            boolean not null default false,
    notes                 text,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    deleted_at            timestamptz
);
create index if not exists performed_session_idx on public.performed_exercises(session_id) where deleted_at is null;
create index if not exists performed_exercise_idx on public.performed_exercises(user_id, exercise_id) where deleted_at is null;

create table if not exists public.actual_sets (
    id                    uuid primary key,
    user_id               uuid not null references auth.users(id) on delete cascade,
    performed_exercise_id uuid not null references public.performed_exercises(id) on delete cascade,
    set_number            integer not null check (set_number > 0),
    weight_kg             numeric(7,2) not null check (weight_kg >= 0),
    reps                  integer not null check (reps >= 0),
    is_warmup             boolean not null default false,
    rir                   integer,
    notes                 text,
    completed_at          timestamptz,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now(),
    deleted_at            timestamptz
);
create index if not exists sets_performed_idx on public.actual_sets(performed_exercise_id) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- Body check-ins
-- ---------------------------------------------------------------------------
-- Lengths are stored in CENTIMETRES and weight in KILOGRAMS regardless of the user's
-- display preference, so switching units never rewrites or invalidates history.
create table if not exists public.body_metrics (
    id                uuid primary key,
    user_id           uuid not null references auth.users(id) on delete cascade,
    metric_date       date not null,
    weight_kg         numeric(6,2) check (weight_kg > 0),
    chest_cm          numeric(6,2) check (chest_cm > 0),
    waist_cm          numeric(6,2) check (waist_cm > 0),
    biceps_left_cm    numeric(6,2) check (biceps_left_cm > 0),
    biceps_right_cm   numeric(6,2) check (biceps_right_cm > 0),
    shoulders_cm      numeric(6,2) check (shoulders_cm > 0),
    thigh_cm          numeric(6,2) check (thigh_cm > 0),
    calf_cm           numeric(6,2) check (calf_cm > 0),
    neck_cm           numeric(6,2) check (neck_cm > 0),
    forearm_cm        numeric(6,2) check (forearm_cm > 0),
    body_fat_percent  numeric(5,2) check (body_fat_percent between 0 and 100),
    notes             text,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    deleted_at        timestamptz
);
create index if not exists body_user_date_idx
    on public.body_metrics(user_id, metric_date desc) where deleted_at is null;

-- ---------------------------------------------------------------------------
-- updated_at maintenance
-- ---------------------------------------------------------------------------
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

do $$
declare t text;
begin
    foreach t in array array[
        'profiles','exercises','programmes','workout_templates','planned_exercises',
        'workout_sessions','performed_exercises','actual_sets','body_metrics'
    ] loop
        execute format(
            'drop trigger if exists touch_%1$s on public.%1$s;
             create trigger touch_%1$s before update on public.%1$s
             for each row execute function public.touch_updated_at();', t);
    end loop;
end $$;

-- ---------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------
-- Policies are written per-command with an explicit `to authenticated` role so that the
-- anonymous role has no access at all, and split rather than combined with `for all` so a
-- future change to one command cannot silently widen the others.

alter table public.profiles            enable row level security;
alter table public.exercises           enable row level security;
alter table public.programmes          enable row level security;
alter table public.workout_templates   enable row level security;
alter table public.planned_exercises   enable row level security;
alter table public.workout_sessions    enable row level security;
alter table public.performed_exercises enable row level security;
alter table public.actual_sets         enable row level security;
alter table public.body_metrics        enable row level security;

drop policy if exists profiles_select on public.profiles;
drop policy if exists profiles_insert on public.profiles;
drop policy if exists profiles_update on public.profiles;
drop policy if exists profiles_delete on public.profiles;
create policy profiles_select on public.profiles for select to authenticated using ((select auth.uid()) = id);
create policy profiles_insert on public.profiles for insert to authenticated with check ((select auth.uid()) = id);
create policy profiles_update on public.profiles for update to authenticated using ((select auth.uid()) = id) with check ((select auth.uid()) = id);
create policy profiles_delete on public.profiles for delete to authenticated using ((select auth.uid()) = id);

do $$
declare t text;
begin
    foreach t in array array[
        'exercises','programmes','workout_templates','planned_exercises',
        'workout_sessions','performed_exercises','actual_sets','body_metrics'
    ] loop
        execute format('drop policy if exists %1$s_select on public.%1$s;', t);
        execute format('drop policy if exists %1$s_insert on public.%1$s;', t);
        execute format('drop policy if exists %1$s_update on public.%1$s;', t);
        execute format('drop policy if exists %1$s_delete on public.%1$s;', t);
        execute format(
            'create policy %1$s_select on public.%1$s for select to authenticated
                 using ((select auth.uid()) = user_id);', t);
        execute format(
            'create policy %1$s_insert on public.%1$s for insert to authenticated
                 with check ((select auth.uid()) = user_id);', t);
        execute format(
            'create policy %1$s_update on public.%1$s for update to authenticated
                 using ((select auth.uid()) = user_id)
                 with check ((select auth.uid()) = user_id);', t);
        execute format(
            'create policy %1$s_delete on public.%1$s for delete to authenticated
                 using ((select auth.uid()) = user_id);', t);
    end loop;
end $$;

-- Create the profile row automatically on signup so the client never has to.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id) values (new.id) on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

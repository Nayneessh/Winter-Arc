# Database

Two stores with the same shape: **Room** on the device (the source of truth) and **Postgres**
in Supabase (optional backup). Identical ids and identical semantics, so a row means the same
thing in both places.

## Design principles

**1. Client-generated UUID primary keys.** Rows are created on the device, often with no
connectivity, and pushed later. A server-assigned sequence would make offline inserts
impossible to reconcile; UUIDs mean an id is decided at the moment of creation and never
changes.

**2. The plan is copied into history, not referenced.** `performed_exercises` carries its own
`planned_sets`, `planned_rep_low`, `planned_rep_high`, `planned_weight_kg`, `planned_rest_seconds`
and `planned_style`. These are snapshots taken when the session started. There is no foreign
key from a session back to a template's current values, so editing a template cannot alter
what a past workout says it prescribed.

**3. Canonical units.** Weight is always kilograms; lengths are always centimetres. Display
units are a presentation concern. Switching from kg to lb changes what is rendered and nothing
that is stored, so history stays comparable across a unit change.

**4. Soft deletes.** Every table carries `deleted_at`. A delete performed offline still
propagates when connectivity returns, and a row deleted on one device is not resurrected by
another device that still holds it.

**5. Sync metadata on every row.** `updated_at` and (locally) `synced_at`. A row is dirty when
`synced_at` is null or older than `updated_at` — one rule, no outbox table to keep consistent.

---

## Tables

### `exercises` — the movement library

| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `name` | text | Duplicates permitted, warned about |
| `primary_muscle` | text | `MuscleGroup` name |
| `equipment` | text | `Equipment` name |
| `default_rest_seconds` | int | ≥ 0 |
| `notes` | text | Execution cue |
| `is_custom` | bool | User-created |
| `is_archived` | bool | Hidden, not deleted |

Duplicate names are allowed on purpose: two gyms genuinely have differently loaded machines
with the same label. The UI warns before creating one.

### `programmes` → `workout_templates` → `planned_exercises` — the PLAN

`programmes` holds a named training block; exactly one is active.

`workout_templates` is a day within it: `day_of_week` (1–7, nullable), `day_type`
(`TRAINING` / `REST` / `ACTIVE_RECOVERY` / `MMA` / `CUSTOM`), `position`, `muscle_groups`.
Rest and MMA days are first-class — not every day has a workout.

`planned_exercises` is a prescription: `sets`, `rep_low`, `rep_high`, `target_weight_kg`,
`rir`, `rest_seconds`, `style`, `position`, and `superset_group`. Entries sharing a non-null
`superset_group` are performed together — the workbook's A1/A2 notation.

Constraints: `sets > 0`, `rep_low > 0`, `rep_high >= rep_low`, `target_weight_kg >= 0`.

### `workout_sessions` → `performed_exercises` → `actual_sets` — the ACTUAL

`workout_sessions` has a **nullable** `template_id`: a session need not come from a plan at
all, which is what makes "train something else" a first-class path rather than a workaround.
`status` is `IN_PROGRESS` / `COMPLETED` / `ABANDONED`.

`performed_exercises` carries the plan snapshot described above, plus provenance:

| Column | Meaning |
|---|---|
| `replaced_exercise_id` | What was prescribed, when the user swapped movements on the day |
| `is_ad_hoc` | Added mid-session, not part of the plan |
| `is_skipped` | Prescribed but not performed; the prescription stays on the record |

`actual_sets` is one row per set performed: `set_number`, `weight_kg`, `reps`, `is_warmup`,
`rir`, `notes`, `completed_at`.

There is **no `is_extra` column**, deliberately. Extra sets are derived:
`max(0, working_sets − planned_sets)`. Storing a flag at write time would go stale if the
snapshot were ever corrected; deriving it means the planned/extra split is always consistent
with the two numbers it comes from.

Constraints: `set_number > 0`, `weight_kg >= 0`, `reps >= 0`. Zero reps is valid — a failed
set is a real event — and contributes no volume.

### `body_metrics` — check-ins

One row per check-in: `weight_kg`, `chest_cm`, `waist_cm`, `biceps_left_cm`, `biceps_right_cm`,
`shoulders_cm`, `thigh_cm`, `calf_cm`, `neck_cm`, `forearm_cm`, `body_fat_percent`, `notes`.

**Every measurement is nullable.** A blank field means *not measured*, never zero. Analytics
skips nulls rather than treating them as data, so a check-in where only weight was recorded
does not corrupt the chest trend.

### `profiles` (Supabase only)

`display_name`, `height_cm`, `weight_unit`, `length_unit`. Created automatically by a trigger
on `auth.users` insert, so the client never has to.

---

## Security

Row-level security is enabled on **every** table, with policies scoped to `auth.uid()`.

Policies are written per-command (`select` / `insert` / `update` / `delete`) rather than as a
single `for all`, so a future change to one command cannot silently widen the others. Each
names `to authenticated` explicitly, which leaves the anonymous role with no access at all.

```sql
create policy actual_sets_select on public.actual_sets
    for select to authenticated
    using ((select auth.uid()) = user_id);
```

`auth.uid()` is wrapped in a scalar subselect so Postgres evaluates it once per query rather
than once per row.

**Trigger functions are not callable over the API.** PostgREST exposes every function in the
`public` schema as an RPC endpoint, which initially left the two `SECURITY DEFINER` trigger
functions reachable by both anon and authenticated roles. `EXECUTE` is revoked from all three
roles; triggers still invoke them through the table owner. The Supabase security advisor
reports **zero findings**.

**Keys.** The publishable (anon) key ships in the client — that is what it is for, and it
grants nothing on its own since RLS decides every access. The **service-role key bypasses RLS**
and is not in this repository, not in the APK, and must never be committed.

---

## Migrations

Versioned SQL under `supabase/migrations/`, applied in filename order.

| File | Contents |
|---|---|
| `20260904000100_initial_schema.sql` | Tables, indexes, `updated_at` triggers, RLS policies, new-user trigger |
| `20260904000200_harden_trigger_functions.sql` | Revokes RPC `EXECUTE` on the trigger functions |

Room schema JSON is exported to `app/schemas/` so future migrations can be diffed against a
known-good starting point rather than reconstructed from memory.

---

## Indexes

Built for the two queries that actually run often:

- `sessions_user_date_idx` on `(user_id, session_date desc)` — history, always newest first
- `performed_exercise_idx` on `(user_id, exercise_id)` — per-exercise history and PR detection
- `body_user_date_idx` on `(user_id, metric_date desc)` — measurement trends

All are partial (`where deleted_at is null`), so tombstones do not bloat them.

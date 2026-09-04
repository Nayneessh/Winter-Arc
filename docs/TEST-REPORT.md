# Winter Arc — Test Report

## Summary

| | |
|---|---|
| Kotlin source | ~10,100 lines, 38 files |
| Automated tests | 92 (80 domain + 12 mapper), all passing |
| Supabase security advisors | 0 findings |
| Verified on a physical device or emulator | **No** — see Limitations |

---

## What was implemented

### Training engine
- Loads today's prescribed workout from the active programme, or starts a blank session
- Logs actual weight, reps and sets with no ceiling from the prescription
- Extra sets and extra reps recorded as extra; the target is never overwritten
- Warm-up sets recorded and excluded from volume, PRs and set counts
- Add an exercise mid-session; create a new one that has never existed before
- Replace an exercise for one session, preserving what was originally prescribed
- Reorder, skip, remove, superset, and adjust the target mid-session
- Session persisted after **every** mutation, not at the end

### Rest timer
- Full-screen night green, countdown in gold at display size
- Pause, resume, skip, +15s, −15s
- Vibration and tone on completion, both toggleable
- Monotonic deadline rather than a decrementing counter, so it stays accurate if the
  coroutine is delayed or the screen sleeps
- Entirely on-device; touches no network

### History
- Every completed session kept permanently with its plan snapshot
- Session detail showing planned vs actual per exercise, extra sets, replacements, skips
- Per-exercise history: times performed, heaviest weight, best reps, best session volume,
  best estimated 1RM, first and last date, progression charts

### Analytics
- Volume (`weight × reps`, working sets only) by exercise, session, week, month
- Estimated 1RM via Epley, matching the source workbook
- PR detection across four categories against real prior performance
- Consistency: week streak, longest streak, sessions this week/month, 4-week average

### Body
- Check-ins with 11 measurements plus body fat, all optional
- Deltas per measurement, weight trend chart
- Estimated fat/lean split when body-fat data exists at both ends

### Dashboard
- Workout, training, strength and body sections
- Weekly volume bars, muscle-group volume donut, strength progression line, bodyweight line
- Goal progress rings against the programme's stated milestones

### Platform
- Room as source of truth; full offline operation
- Optional Supabase backup with email auth, RLS, and offline-safe conflict handling
- JSON and CSV export via share sheet
- Edge-to-edge, system back navigation, configuration-change handling, keep-screen-on

---

## Automated test results

### Domain logic — 80 tests, 0 failures
Run locally and in CI. Pure Kotlin/JVM, no Android SDK required.

| Suite | Tests | Covers |
|---|---|---|
| `WorkoutEngineTest` | 23 | Plan snapshotting, extra/missed sets, warm-ups, zero-rep sets, decimal weights, negative-input rejection, set renumbering, replacement provenance, ad-hoc exercises, reorder, skip, superset, mid-session plan edits, blank sessions, frozen-session enforcement, duration, aggregate totals |
| `AnalyticsTest` | 31 | Epley against the workbook's own figures, single-rep case, reliability ceiling, all four PR types, PR noise rules, bodyweight rep progression, warm-up exclusion, self/future-session exclusion, volume aggregation by week and month, streaks across rest days, goal-progress clamping, donut share arithmetic |
| `BodyAnalyticsTest` | 15 | Delta ordering, null-field skipping, fat-mass preconditions, unit conversion round-trips, and assertions that weight-change wording contains neither "fat" nor "muscle" |
| `SeedTest` | 11 | Referential integrity, id uniqueness, one active programme, seven-day coverage, empty rest/MMA days, rep-range coherence, contiguous positions, superset pairing, arm/back volume matching the workbook's stated V2 allocation, baseline conversions, startability of every seeded template |

### Mapper round-trips — 12 tests, 0 failures
Entity ↔ domain in both directions, field by field. Added because a copy-paste slip in a
mapper silently swaps two fields and fails months later as history that disagrees with what
was performed. Covers the plan snapshot, the eleven-nullable-double body metric block,
null preservation, and enum fallback for rows arriving from a newer app version.

### Notable bugs these caught
1. **Rep-PR noise.** The original rule ("more reps at that weight *or heavier*") let 20 reps
   at 40 kg register as a rep PR over 5 reps at 100 kg. Tightened to an exact weight match,
   with a bodyweight-progression test to prove pull-ups still work.
2. **Seed integrity.** `SeedTest` caught a superset group in the archived V1 programme with
   only one member, and a calf raise the workbook's own plan review had restored but which
   had not been carried over.
3. **`lastNotNullOfOrNull` does not exist** in the Kotlin stdlib — caught at first compile.

---

## Build verification

CI (`.github/workflows/android.yml`) runs on every push:

1. Domain unit tests
2. App unit tests
3. Android Lint (non-blocking)
4. `assembleDebug`
5. `assembleRelease` (R8 minification + resource shrinking)
6. **APK structure verification** — each archive is opened and checked for
   `AndroidManifest.xml` and `classes.dex`, so a green build is not mistaken for an
   installable artifact
7. Artifact upload

### CI history
| Run | Result | Cause |
|---|---|---|
| 1 | Fail | `:app` plugin IDs had no version after the root `plugins` block was removed |
| 2 | Fail | Stack traces flooded the log; removed `--stacktrace` to make errors readable |
| 3 | Fail | `java.util.Properties()` — in a Gradle Kotlin DSL script `java` resolves to the JavaPluginExtension, not the package |
| 4 | Fail | Eight Kotlin errors, all one root cause: extension members cannot be fully qualified, only imported |
| 5 | Domain + app tests pass; APK build reached | — |

---

## Supabase

| | |
|---|---|
| Project | `winter-arc` (`bwnruvqapzzygmhtgejb`), region `ap-south-1` |
| Tables | 9, all with RLS enabled |
| Policies | Per-command (`select`/`insert`/`update`/`delete`), scoped `to authenticated`, `auth.uid() = user_id` |
| Migrations | 2, versioned in `supabase/migrations/` |
| Security advisors | **0 findings** |

An initial advisor run reported four warnings: both `SECURITY DEFINER` trigger functions were
callable as RPC by the anon and authenticated roles, because PostgREST exposes every function
in the `public` schema. `EXECUTE` was revoked from all three roles; triggers still invoke them
through the table owner. Re-run: clean.

---

## Limitations — read this section

**1. Not run on a device or emulator.** This is the significant one. The build environment
blocks `dl.google.com` at the network policy level, which serves both the Android SDK and the
Android Gradle Plugin, so no Android build or emulator was possible locally. The APK is
compiled and structurally verified in CI, but **no screen has been rendered and no user
journey has been executed**. The acceptance scenario in the brief — start workout, log a set,
watch the timer turn the screen green, add an extra set, replace an exercise, finish, reopen,
check history — has not been performed.

What *is* verified: every calculation behind those screens, by 92 tests.

**2. No instrumented tests.** Room migrations, DAO queries against real SQLite, and Compose UI
tests all require a device. The DAO SQL is compile-time verified by Room's annotation
processor (KSP succeeds, which means every `@Query` parses and type-checks against the schema),
but has not been executed.

**3. Sync has not been exercised end to end.** The schema, policies and client are in place and
the push path is written, but no round trip has been performed against the live project. Sync
is optional and switched off by default, so this does not affect core use.

**4. Pull-down sync is not implemented.** The engine pushes local changes. It does not yet
reconcile changes made on a second device. For a single-user, single-device app this is the
correct V1 scope, but it is a stated gap rather than an oversight — `selectSince` exists on the
client for when it is needed.

**5. Estimated 1RM above 12 reps.** Epley drifts high at high rep counts. The app flags these
points rather than hiding them.

**6. Progress photos, nutrition, MMA and recovery tracking** were deliberately excluded from
V1 per the brief. The schema and module boundaries leave room for them.

---

## Recommended first session on the device

In this order, because each step feeds the next:

1. Install, open. Home should show today's workout from the V2 programme.
2. Start it. Log one set at the prescribed weight.
3. Watch the rest timer take the screen. Try pause, +15s, skip.
4. Log a set at a different weight, then press **+ Add set** past the prescribed count and
   confirm the header shows it as extra.
5. Replace one exercise. Confirm the card says what it replaced.
6. Finish. Check the summary counts planned vs actual vs extra.
7. Force-close the app, reopen, open History. The session must be there, unchanged.
8. Add a body check-in. Confirm the weight statement says "Weight change", never "fat lost".
9. Turn off mobile data and Wi-Fi. Repeat steps 2–7. Everything must work.

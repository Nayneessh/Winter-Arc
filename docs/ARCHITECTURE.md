# Architecture

## The shape of the problem

A training app has one hard requirement that most CRUD apps do not: **the past must not
change.** A user edits their programme in November; the September workout must still read
exactly as it did in September. Every structural decision below follows from that.

## Modules

```
winter-arc/
├── core/domain/     Pure Kotlin/JVM. No Android. All business logic.
└── app/             Android: Room, Compose, sync, DI container.
```

`:core:domain` has no Android dependency and resolves entirely from Maven Central. That is a
deliberate constraint, not an accident of layering: it means the entire body of logic that
produces the numbers a user trusts — volume, estimated 1RM, PR detection, plan reconciliation,
streaks, body deltas — compiles and tests on any machine with a JDK, with no SDK, no emulator
and no device.

`WINTER_ARC_DOMAIN_ONLY=1` drops `:app` from the Gradle build entirely, so the suite runs even
where the Android Gradle Plugin cannot be resolved.

### Contents

| Package | Responsibility |
|---|---|
| `domain.model` | Training and body types. Plan and actual are separate types. |
| `domain.programme` | `WorkoutEngine` — pure transformations over a session. |
| `domain.analytics` | Volume, Epley 1RM, PR detection, consistency, composition, body deltas. |
| `domain.seed` | First-run programme content derived from the source workbook. |

---

## How history stays immutable

Two mechanisms, both in the domain layer where they can be tested.

### 1. The plan is snapshotted at session start

`WorkoutEngine.startFromTemplate` copies the prescribed sets, reps, weight, rest and style
**into each `PerformedExercise`**. A session does not hold a pointer to its template's current
values; it holds a copy of what they were when it began.

```kotlin
PerformedExercise(
    plannedSets = planned.sets,          // copied, not referenced
    plannedRepLow = planned.repLow,
    plannedWeightKg = planned.targetWeightKg,
    ...
)
```

Editing a template later cannot reach a session that already exists. There is no code path
that would allow it, because there is no relationship to traverse.

### 2. Completed sessions are frozen

Every mutating operation on `WorkoutEngine` begins with `requireEditable`, which throws
`SessionFrozen` for any session whose status is not `IN_PROGRESS`. Logging, editing, replacing,
reordering and removing all go through it.

The result: an accidental write to finished history is a caught exception at the boundary, not
a silent corruption discovered months later.

---

## Data flow

```
Compose UI
   │  events
   ▼
ViewModel ──────────► WorkoutEngine (pure, returns a NEW session)
   │                        │
   │  StateFlow             │ transformed session
   ▼                        ▼
Compose UI ◄──────── TrainingRepository ──► Room  (source of truth)
                              │
                              │ dirty rows
                              ▼
                         SyncEngine ──► Supabase (optional, additive)
```

**State** is unidirectional. ViewModels expose an immutable `StateFlow`; the UI sends events
back. Engine operations are pure functions returning a new session, so a transformation can be
tested without a database, a device or a coroutine.

**Persistence happens on every change**, not at the end of a workout. A phone killed mid-session
or a mis-tapped back gesture must not lose logged sets, so `WorkoutViewModel.apply` saves
immediately after every mutation. The whole session graph is written inside one Room
transaction — a workout is only meaningful as a whole, and a partial write would leave sets
attached to an exercise that does not exist.

---

## Offline-first

Room is the source of truth. Not a cache — the truth. The app has no concept of "loading from
the server"; it reads local data and renders it.

Supabase is strictly additive. With no credentials compiled in, no account, or no signal, every
feature works: logging, history, dashboard, charts, body tracking, export. The only thing that
stops is backup.

### Sync

Push-based, driven by a single rule: a row is dirty when `syncedAt` is null or older than
`updatedAt`. No separate outbox table to keep consistent.

**Conflict resolution**

| Situation | Resolution | Why |
|---|---|---|
| Two devices create rows offline | No conflict possible | Client-generated UUIDs |
| Push succeeds, response lost | Idempotent retry | Upsert with `merge-duplicates` |
| Same row edited on two devices | Later `updated_at` wins | Per-field merge is complexity a single user does not need |
| **Completed session differs from server** | **Local wins. Never overwritten by a pull.** | A finished workout records something that physically happened. If the server disagrees with the device that recorded it, the device is right. |

That last row is the one place last-write-wins is deliberately rejected.

Rows are pushed parent-first (exercises → programmes → templates → planned → sessions →
performed → sets) so a foreign key never arrives before the row it points at. Deletes are soft
(`deleted_at`) so a delete made offline still propagates, and a row deleted on one device is not
resurrected by another that still holds it.

---

## Navigation

Navigation Compose, single activity, platform back stack throughout — the system back gesture
and button behave exactly as expected on every screen.

One interception: during an active workout, back navigates home and **leaves the session
running**. Nothing is discarded. Discarding requires an explicit confirmed action.

```
home ─┬─ workout?template={id}&custom={bool} ─► complete
      ├─ history ─► session detail ─► exercise history
      ├─ dashboard
      ├─ body
      └─ settings ─┬─ library
                   └─ templates ─► template editor
```

---

## Dependency injection

`AppContainer`, constructed once by the `Application`, holding lazy singletons.

Hilt was considered and rejected. It would add an annotation processor and a compile-time graph
to an app with one user, one database and eight screens — cost without matching benefit, and one
more component that can fail a release build. The container is about twenty lines and the wiring
is readable in one screen.

---

## UI

Jetpack Compose with Material 3, edge-to-edge, dark by design. The app is used in a gym in low
light; a light theme would undermine the identity for no practical gain.

**Design system** — night green carries the surface hierarchy, gold is reserved for numbers and
primary actions, night blue appears sparingly for secondary and informational states. If
everything is highlighted, nothing is.

**Sizing for the actual context.** Controls are oversized (60dp primary buttons, 52dp steppers)
because they are operated with chalky, sweaty hands between sets. Weight and reps pre-fill from
the previous set, so the common case — repeating a set — is a single tap.

**Charts** are drawn directly on a Compose canvas. Four chart types at roughly a hundred lines
each, against a dependency plus a theming fight plus release-build risk.

---

## Testing

| Layer | Approach |
|---|---|
| Domain | 80 JUnit tests, run locally and in CI. Covers the engine, all analytics, unit conversion and seed integrity. |
| App unit | Runs in CI. |
| Lint | Android Lint in CI, non-blocking. |
| APK structure | CI verifies each APK contains a manifest and dex rather than trusting a green build. |

The domain tests are the ones that matter most: they cover every number the user is asked to
trust, and they run without a device.

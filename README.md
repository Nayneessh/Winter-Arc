# Winter Arc

A personal training log for Android. Not a workout-plan viewer — a permanent record of what
was actually lifted, and what changed as a result.

The architecture follows one idea:

```
PROGRAMME  →  TODAY'S PLAN  →  ACTUAL WORKOUT  →  PERMANENT HISTORY  →  PROGRESS
```

The plan says what to do. The session records what was done. They are separate, and history
is never rewritten when the plan changes.

---

## What it does

**Training**
- Loads today's prescribed workout, or lets you train something else entirely
- Logs actual weight, reps and sets — never constrained by what was prescribed
- Extra sets and extra reps are recorded as extra, without overwriting the target
- Add an exercise mid-session; create one that has never existed before
- Replace an exercise for one workout without corrupting any past record
- Reorder exercises, build and break supersets, skip a movement, adjust a target
- Full-screen rest countdown in night green with the timer in gold: pause, resume, skip, ±15s

**History**
- Every completed workout kept permanently, exactly as performed
- Per-exercise history: first and latest performance, heaviest weight, best reps, best volume,
  best estimated 1RM, times performed, progression charts
- Personal records detected automatically against real prior performance

**Progress**
- Dashboard covering workouts, consistency, strength, volume and body
- Volume by week, volume share by muscle group, strength progression, bodyweight trend
- Body check-ins with measurement deltas and, when body-fat data exists, an estimated
  composition split

**Your data**
- Works fully offline, forever. No account required
- Optional Supabase backup
- JSON and CSV export

---

## The numbers, defined

Every figure in the app has one definition, used everywhere.

| Term | Definition |
|---|---|
| **Volume** | `weight_kg × reps`, summed over working sets. Warm-ups and zero-rep sets contribute nothing. |
| **Estimated 1RM** | Epley: `weight × (1 + reps ÷ 30)`. A single rep returns the weight itself. Flagged as unreliable above 12 reps. **Always an estimate, never a tested max.** |
| **Extra sets** | `actual working sets − planned sets`, floored at zero. |
| **Streak** | Consecutive **weeks** containing at least one completed session. |
| **Weight change** | A change in scale weight. Never described as fat lost or muscle gained. |
| **Fat mass change** | Only computed when you supply body-fat percentages at both ends. Always labelled an estimate. |

### Why streaks count weeks, not days

The programme prescribes a mandatory Sunday rest day. A day-based streak would reset every
week *by design*, punishing correct adherence. Weeks measure the thing that matters and cannot
be broken by planned recovery.

### Why the donut chart shows volume, not "progress composition"

A pie or donut is only honest when its slices are parts of one whole, measured in one unit.
Volume share by muscle group satisfies that: every slice is kilograms, and they sum to total
volume.

"Strength improvement vs weight progression vs rep progression" does not. Those are unrelated
rates in different units that do not sum to anything. Drawing them as one circle would invent
a total that does not exist. They are shown as **separate progress rings** instead — each
measuring one target's own distance travelled against distance required.

---

## Get the APK

Every push builds and verifies both APKs in CI.

1. Open the [Actions tab](https://github.com/Nayneessh/Winter-Arc/actions) and pick the
   latest green run on `claude/lucid-sagan-y9pd6x`
2. Scroll to **Artifacts**
3. Download **`winter-arc-debug-apk`**, unzip, install the `.apk` inside

Install the **debug** build first: it is not minified, so a crash gives a readable stack
trace, and its application id is `com.winterarc.app.debug`, so it can sit alongside a release
build without conflict.

```bash
adb install -r app-debug.apk   # or just open the file on the phone
```

## Build it yourself

```bash
git clone https://github.com/Nayneessh/Winter-Arc.git
cd Winter-Arc
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Requires JDK 17+ and the Android SDK (compileSdk 35). Full instructions, including how CI
builds it, are in [docs/BUILDING.md](docs/BUILDING.md).

### Environment configuration

Supabase is **optional**. With no credentials the app is fully functional; cloud backup is
simply switched off.

| Key | Where it comes from |
|---|---|
| `SUPABASE_URL` | env var → `secrets.properties` → `supabase.properties` |
| `SUPABASE_ANON_KEY` | same order |

`supabase.properties` is committed and holds the **publishable** key. That key is designed to
ship inside client apps and grants no authority on its own — row-level security decides every
read and write. The **service-role** key bypasses RLS, is not in this repository, is not in the
APK, and must never be committed. Genuinely secret values go in `secrets.properties`, which is
git-ignored.

---

## Development

```bash
# Business logic tests (80). Runs without the Android SDK.
WINTER_ARC_DOMAIN_ONLY=1 ./gradlew :core:domain:test

# Everything (92 tests)
./gradlew :core:domain:test :app:testDebugUnitTest :app:lintDebug
```

`WINTER_ARC_DOMAIN_ONLY=1` drops the Android module from the build. `:core:domain` is pure
Kotlin/JVM and resolves entirely from Maven Central, so the whole business-logic suite runs on
a machine with no Android SDK at all.

---

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — modules, data flow, sync, state, navigation
- [docs/DATABASE.md](docs/DATABASE.md) — schema, both local and cloud, and why it is shaped this way
- [docs/BUILDING.md](docs/BUILDING.md) — building and releasing the APK
- [docs/DECISIONS.md](docs/DECISIONS.md) — the engineering calls made, and what they cost
- [docs/TEST-REPORT.md](docs/TEST-REPORT.md) — what was tested, what passed, what was not

---

## Source material

The programme was derived from a 25-sheet planning workbook. Exercises, order, pairings, sets,
rep ranges, RIR, rest, style and execution cues were carried over. Dashboards, volume audits,
version-comparison tables, change logs and nutrition trackers were not — none of them change
what happens between two sets.

The workbook contained a 4-day V1 and a 3-day V2 with a change log documenting the move.
**V2 is seeded active**; V1 is seeded archived so nothing is lost and either can be run.

Everything seeded is editable in the app. After first run the seed is never read again.

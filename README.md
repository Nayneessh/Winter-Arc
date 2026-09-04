# Winter Arc

A training log for Android. Not a plan viewer — a permanent record of what was actually
lifted, and what changed as a result.

```
PROGRAMME  →  TODAY  →  THE SESSION  →  PERMANENT HISTORY  →  PROGRESS
```

The plan says what to do. The session records what was done. They are separate, and history is
never rewritten when the plan changes.

---

## Get the APK

The `apk` branch always holds the current build:

```bash
git clone --branch apk --depth 1 https://github.com/Nayneessh/Winter-Arc.git winter-arc-apk
```

Or download **`winter-arc-debug-apk`** from the latest green run on the
[Actions tab](https://github.com/Nayneessh/Winter-Arc/actions).

Install `app-debug.apk`. It is not minified, so a crash produces a readable stack trace, and its
application id is `com.winterarc.app.debug`, so it can sit alongside a release build. Android
will ask you to allow installing from your browser or file manager.

Requires Android 8.0 or later.

---

## What it does

**Train**
- Loads today's prescribed session, or lets you run something else entirely
- Every set pre-filled from the last time you performed that movement
- A full-screen numeric keypad instead of the system keyboard, with ±1 and ±2.5 as single taps
- Add sets past the prescription, add a movement mid-session, swap one for another, skip one,
  reorder, adjust a target for today only
- Full-screen rest countdown, started automatically when a set is ticked
- Finishing shows what you did, and any records you actually beat

**Everything else**
- A dashboard covering targets, strength, volume, sets, reps, records, body and consistency
- Per-movement history: every performance, load over time, estimated 1RM, volume per session
- Body check-ins: weight, body fat, nine measurement sites, with trends and composition
- Build your own routines and movements — the app is not tied to this programme
- Works entirely offline, forever. No account, no server
- JSON backup and CSV export of every set

---

## The numbers, defined

Every figure has one definition, used everywhere.

| Term | Definition |
|---|---|
| **Volume** | `weight × reps`, summed over completed working sets. Warm-ups and zero-rep sets contribute nothing. |
| **Estimated 1RM** | Epley: `weight × (1 + reps ÷ 30)`. A single rep returns the weight itself. Flagged unreliable above 12 reps. **Always an estimate, never a tested max.** |
| **Strength index** | Summed best estimated 1RM across everything trained in a week, indexed to 100 at the first week with data. A trend, not a load — it moves when exercise selection moves. |
| **Extra sets** | `working sets performed − sets prescribed`, floored at zero. |
| **Streak** | Consecutive **weeks** containing at least one completed session. |
| **Personal record** | Beating your own prior best for that movement. A first-ever performance is not a record. |
| **Fat / lean mass** | Only computed from check-ins carrying **both** a weight and a body-fat percentage. Always labelled an estimate. |

**Why streaks count weeks.** The programme prescribes a mandatory Sunday rest. A day-based
streak would reset every week *by design*, punishing correct adherence.

**Why the donut shows volume.** A ring is only honest when its slices are parts of one whole in
one unit. Volume share by muscle group satisfies that — every slice is kilograms and they sum to
the total. "Strength vs weight vs rep progression" does not: those are unrelated rates in
different units that sum to nothing. They are drawn as separate rings instead.

---

## Architecture

Two modules.

**`:core`** — pure Kotlin/JVM. The model, every calculation, the seeded programme, and every
state transition as a pure `AppData -> AppData` function. 80 tests, no Android SDK required:

```bash
WINTER_ARC_CORE_ONLY=1 ./gradlew :core:test
```

**`:app`** — Android and Compose. Rendering and event forwarding, nothing else.

The split is not decoration. `dl.google.com` is blocked by network policy in the environment
this was built in, and that host serves both the Android SDK and the Android Gradle Plugin, so
no Android build could run there at all. Putting the whole behaviour in a module that compiles
and tests on a bare JDK meant none of it shipped unexecuted; APK assembly happens in CI.

### Storage

One JSON document, written atomically with one generation of backup. At roughly a megabyte per
training year the entire state fits in memory, which removes an annotation processor, a schema
migration path and a query language — and makes export trivial. `AppData` is the single type a
database would ever have to replace, and nothing above it would change.

Saves are debounced by 350 ms, because logging a set is a burst of edits and IO should never sit
in the way of the one interaction that has to feel instant. A pending save is flushed
synchronously when the app goes to the background.

---

## Build it yourself

```bash
git clone https://github.com/Nayneessh/Winter-Arc.git
cd Winter-Arc
./gradlew :app:assembleDebug     # app/build/outputs/apk/debug/
```

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26).

---

## The programme

Transcribed from the source planning workbook: six routines, their order, supersets, sets, rep
ranges, RIR, rest, style and every execution cue.

| Day | Session |
|---|---|
| Mon | MMA + Back & Rear Delt — 30 min light, after MMA |
| Tue | Heavy Push + Quads — 90 min |
| Wed | MMA + Arms — 30 min light, after MMA |
| Thu | Heavy Pull + Posterior — 90 min |
| Fri | MMA + Chest & Delts — 30 min light, after MMA |
| Sat | Arm Day — the priority session |
| Sun | Rest — prescribed, not earned |

Everything seeded is ordinary editable data. After the first run the seed is never read again,
so changing a rep range or deleting a movement sticks.

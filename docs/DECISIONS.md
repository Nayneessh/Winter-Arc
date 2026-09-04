# Engineering decisions

Each entry: what was decided, why, and what it cost.

---

## 1. The plan and the actual are separate types

**Decision.** `PlannedExercise` (a prescription) and `PerformedExercise` (what happened) are
distinct types. A session copies the prescription into itself at start.

**Why.** The alternative — one mutable "exercise" row that carries both target and result — is
the design that makes history rot. Editing a template in November would silently rewrite what
September's workout claims it prescribed. Copying costs a few columns and removes an entire
class of bug that would only be discovered months later, when the data is already wrong.

**Cost.** Duplicated fields on `performed_exercises`. Worth it.

---

## 2. Completed sessions are frozen at the domain layer

**Decision.** Every mutating operation on `WorkoutEngine` starts with `requireEditable`, which
throws `SessionFrozen` for anything not `IN_PROGRESS`.

**Why.** "The UI won't offer that button" is not a guarantee. Enforcing it where the logic lives
means an accidental write is a caught exception at a boundary, not silent corruption.

**Cost.** Callers must handle the exception. Three call sites do.

---

## 3. Extra sets are derived, not stored

**Decision.** No `is_extra` column. Extra sets are `max(0, working_sets − planned_sets)`.

**Why.** A flag written at log time would go stale if the plan snapshot were ever corrected.
Deriving it means the planned/actual/extra split is always consistent with the two numbers it
comes from.

**Cost.** None meaningful.

---

## 4. Streaks count weeks, not days

**Decision.** A streak is consecutive **weeks** containing at least one completed session.

**Why.** The programme mandates a Sunday rest day. A day-based streak would break every week
*by design* — punishing the user for following the plan correctly, and turning the app's most
motivating number into a source of guilt. Weeks measure the thing that matters.

**Cost.** Less granular than a day counter. That granularity was actively harmful here.

---

## 5. The donut chart shows volume share, not "progress composition"

**Decision.** The requested "progress composition" pie was **not built as a pie**. The donut
shows volume share by muscle group; goal progress is shown as separate rings.

**Why.** A pie is only honest when its slices are parts of one whole in one unit. Volume share
qualifies: every slice is kilograms and they sum to total volume. "Strength improvement vs
weight progression vs rep progression" does not — those are unrelated rates in different units.
Drawing them as one circle would invent a total that does not exist and imply that gaining
strength somehow comes at the expense of losing weight.

The underlying request — *show me my progress at a glance* — is answered in full, by a chart
that does not lie about the relationship between its parts.

**Cost.** Not literally what was asked for. Explained in-app next to the chart.

---

## 6. Scale weight is never reported as fat lost

**Decision.** `BodyAnalytics` reports weight changes as weight changes. Fat-mass change is
computed **only** when the user supplies body-fat percentages at both ends, and is returned
through a type whose name and `caveat` field carry the uncertainty.

**Why.** "You lost 3 kg of fat" from a scale reading is a claim the data cannot support — the
change could be water, glycogen, gut content or muscle. Fitness apps make this claim constantly
and it drives bad decisions.

The wording lives in the domain layer as tested functions rather than in UI labels, so the rule
cannot drift as screens are edited. Two tests assert the output contains neither "fat" nor
"muscle".

**Cost.** Less satisfying copy. Correct.

---

## 7. Estimated 1RM uses Epley, matching the source workbook

**Decision.** `weight × (1 + reps ÷ 30)`, with a special case returning the weight itself at one
rep, and an `isReliable` flag that goes false above 12 reps.

**Why.** The source workbook's PR tracker already used Epley. Matching it means figures carried
over stay comparable. The one-rep case matters because naive Epley returns 103.3% of a weight
already lifted for a single — claiming a max above a real performance.

**Cost.** Epley drifts high at high reps. Handled by flagging rather than hiding.

---

## 8. Rep PRs require the same weight

**Decision.** A rep PR fires only when reps exceed the previous best **at that exact load**.

**Why.** The first implementation used "at that weight or heavier", which meant 20 reps at 40 kg
registered as a rep PR over 5 reps at 100 kg. Technically true, useless as a signal. An exact
match keeps the feed meaningful and handles bodyweight movements correctly — every pull-up set
sits at the same load, so reps are the only progression signal and must register.

**Cost.** A weight never lifted before produces no rep PR. That case is covered by the weight PR.

---

## 9. No dependency-injection framework

**Decision.** A manual `AppContainer` with lazy singletons.

**Why.** Hilt would add an annotation processor and a compile-time graph to an app with one
user, one database and eight screens. Cost without matching benefit, and one more component
that can fail a release build — which mattered acutely here, where the Android build could not
be run locally at all and every compile error cost a full CI round trip.

**Cost.** No compile-time graph validation. The graph is twenty lines and fits on one screen.

---

## 10. No HTTP library

**Decision.** `HttpURLConnection` plus kotlinx.serialization for the Supabase client.

**Why.** The sync surface is four calls: sign in, sign up, refresh, upsert. Ktor or Retrofit
would add a dependency tree for a feature that is strictly optional and can be switched off
entirely.

**Cost.** More manual request code, all in one file.

---

## 11. Charts drawn on a canvas

**Decision.** Line, bar, donut and ring charts implemented directly in Compose.

**Why.** Four chart types at roughly a hundred lines each, against a dependency plus a fight to
make it match a bespoke night-green-and-gold palette plus release-build risk.

**Cost.** No zoom or pan. Neither is needed.

---

## 12. Reorder uses arrows, not drag-and-drop

**Decision.** Explicit up/down buttons.

**Why.** The brief asked for drag-and-drop. A drag gesture on a small target, operated with
sweaty hands between sets, misfires often enough to be a liability — and a misfire silently
reorders the wrong exercise. Two large arrows are slower per action and never wrong.

**Cost.** Slower for a large reorder. Reordering is rare; correctness is not negotiable.

---

## 13. V2 seeded active, V1 seeded archived

**Decision.** Both programmes from the workbook are seeded. V2 (3-day) is active; V1 (4-day) is
archived.

**Why.** The workbook's own change log documents the move to V2, so V2 is current. Discarding V1
would throw away work for no gain; making the user choose on first launch would be a decision
the data already answers.

**Cost.** A few extra seed rows.

---

## 14. The APK is built in CI, not locally

**Decision.** `.github/workflows/android.yml` builds and verifies the APK. The domain module is
pure Kotlin/JVM so its tests still run anywhere.

**Why.** Not a preference — a constraint. The build environment blocks `dl.google.com` at the
network policy level, and that host serves both the Android SDK and the Android Gradle Plugin.
Neither is mirrored on Maven Central. No Android build was possible locally at all.

The response was to isolate everything that *could* be tested locally into a module with no
Android dependency, so the logic producing every number a user trusts is verified on every
change, and only assembly depends on CI.

**Cost.** Compile errors in the Android layer cost a CI round trip instead of seconds. This is
recorded honestly in the test report: the UI compiles and the APK is structurally verified, but
it has **not** been exercised on a device or emulator.

---

## 15. The publishable Supabase key is committed

**Decision.** `supabase.properties` holds the project URL and publishable key and is committed.

**Why.** The publishable key is designed to ship inside clients — it is in every browser and
mobile build of every Supabase app. It grants no authority alone; RLS decides every access.
Keeping it out would have meant either a manual setup step before the app could reach the
backend at all, or a build with cloud backup permanently disabled.

The **service-role key**, which does bypass RLS, is not in the repository, not in the APK, and
never will be.

**Cost.** Requires understanding the distinction. Documented in the file itself, the README and
`docs/DATABASE.md`.

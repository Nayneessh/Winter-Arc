package com.winterarc.core

import java.time.LocalDate

/**
 * Every mutation the app can perform, as a pure `AppData -> AppData` function.
 *
 * Keeping transitions here rather than in a ViewModel means the whole behaviour of the app --
 * starting a session, adding a set past the prescription, swapping a movement, finishing --
 * is exercised by plain JVM tests with no device, no emulator and no Android SDK. The Compose
 * layer above is left with nothing but rendering and event forwarding.
 */
object Actions {

    // -- session lifecycle ---------------------------------------------------------------------

    /**
     * Snapshots a routine into a live session.
     *
     * The prescription is COPIED, never referenced. Editing the programme next month must not
     * silently rewrite what today's session said to do, and history must never shift underneath
     * a finished record.
     */
    fun startSession(
        data: AppData,
        routineId: String?,
        date: LocalDate,
        nowMillis: Long,
        titleOverride: String? = null,
    ): AppData {
        val routine = data.routine(routineId)
        val exercises = routine?.items.orEmpty().map { item ->
            SessionExercise(
                exerciseId = item.exerciseId,
                group = item.group,
                plannedSets = item.sets,
                repLow = item.repLow,
                repHigh = item.repHigh,
                rir = item.rir,
                restSeconds = item.restSeconds,
                style = item.style,
                cue = item.cue,
                priority = item.priority,
                sets = prefilledSets(data, item.exerciseId, item.sets, item.repLow, item.targetWeightKg),
            )
        }
        val session = Session(
            date = date,
            startedAtMillis = nowMillis,
            routineId = routine?.id,
            title = titleOverride ?: routine?.name ?: "Open session",
            accent = routine?.accent ?: Accent.GREEN,
            exercises = exercises,
        )
        return data.copy(activeSession = session)
    }

    /**
     * Builds the set rows a movement starts with, pre-loaded from the last time it was actually
     * performed. Walking up to the rack already knowing last week's numbers is the single
     * biggest time saving in the whole app, so it is done by default rather than on request.
     */
    private fun prefilledSets(
        data: AppData,
        exerciseId: String,
        count: Int,
        repLow: Int,
        targetWeightKg: Double?,
    ): List<SetEntry> {
        val last = lastPerformance(data, exerciseId)
        val weight = last?.workingSets?.lastOrNull()?.weightKg ?: targetWeightKg ?: 0.0
        val reps = last?.workingSets?.lastOrNull()?.reps ?: repLow
        return List(count.coerceAtLeast(1)) {
            SetEntry(weightKg = weight, reps = reps, done = false)
        }
    }

    /** The most recent finished performance of a movement, for prefill and "last time" hints. */
    fun lastPerformance(data: AppData, exerciseId: String): SessionExercise? =
        data.sessions
            .filter { it.finished }
            .sortedWith(compareByDescending<Session> { it.date }.thenByDescending { it.startedAtMillis })
            .firstNotNullOfOrNull { session ->
                session.exercises.firstOrNull { it.exerciseId == exerciseId && it.workingSets.isNotEmpty() }
            }

    fun finishSession(data: AppData, nowMillis: Long): AppData {
        val active = data.activeSession ?: return data
        // Rows that were never completed are dropped rather than stored as zeros. A set that did
        // not happen is not a set of zero reps, and leaving it in would drag every average down.
        val cleaned = active.exercises.map { ex ->
            ex.copy(sets = ex.sets.filter { it.done })
        }.filter { it.sets.isNotEmpty() || it.skipped }

        val finished = active.copy(exercises = cleaned, finishedAtMillis = nowMillis)
        return data.copy(
            sessions = data.sessions + finished,
            activeSession = null,
        )
    }

    fun discardSession(data: AppData): AppData = data.copy(activeSession = null)

    fun updateSessionNote(data: AppData, note: String): AppData =
        data.copy(activeSession = data.activeSession?.copy(note = note))

    // -- editing the live session ----------------------------------------------------------------

    private fun mutateActive(data: AppData, block: (Session) -> Session): AppData =
        data.activeSession?.let { data.copy(activeSession = block(it)) } ?: data

    private fun mutateExercise(
        data: AppData,
        sessionExerciseId: String,
        block: (SessionExercise) -> SessionExercise,
    ): AppData = mutateActive(data) { session ->
        session.copy(
            exercises = session.exercises.map { if (it.id == sessionExerciseId) block(it) else it },
        )
    }

    /**
     * Appends one more working set beyond whatever was prescribed.
     *
     * The planned count is deliberately left alone: the difference between what was prescribed
     * and what was performed is real information, and overwriting the target to match the
     * outcome would erase it.
     */
    fun addSet(data: AppData, sessionExerciseId: String): AppData =
        mutateExercise(data, sessionExerciseId) { ex ->
            val template = ex.sets.lastOrNull()
            ex.copy(
                sets = ex.sets + SetEntry(
                    weightKg = template?.weightKg ?: 0.0,
                    reps = template?.reps ?: ex.repLow,
                    done = false,
                ),
            )
        }

    fun removeSet(data: AppData, sessionExerciseId: String, setId: String): AppData =
        mutateExercise(data, sessionExerciseId) { ex ->
            ex.copy(sets = ex.sets.filterNot { it.id == setId })
        }

    fun updateSet(
        data: AppData,
        sessionExerciseId: String,
        setId: String,
        weightKg: Double? = null,
        reps: Int? = null,
        done: Boolean? = null,
        warmup: Boolean? = null,
        rpe: Double? = null,
    ): AppData = mutateExercise(data, sessionExerciseId) { ex ->
        ex.copy(
            sets = ex.sets.map { set ->
                if (set.id != setId) set
                else set.copy(
                    weightKg = (weightKg ?: set.weightKg).coerceAtLeast(0.0),
                    reps = (reps ?: set.reps).coerceAtLeast(0),
                    done = done ?: set.done,
                    warmup = warmup ?: set.warmup,
                    rpe = rpe ?: set.rpe,
                )
            },
        )
    }

    fun toggleSetDone(data: AppData, sessionExerciseId: String, setId: String): AppData =
        mutateExercise(data, sessionExerciseId) { ex ->
            ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(done = !it.done) else it })
        }

    fun setSkipped(data: AppData, sessionExerciseId: String, skipped: Boolean): AppData =
        mutateExercise(data, sessionExerciseId) { it.copy(skipped = skipped) }

    fun updateExerciseNote(data: AppData, sessionExerciseId: String, note: String): AppData =
        mutateExercise(data, sessionExerciseId) { it.copy(note = note) }

    /** Adjusts the prescription for this session only. The programme itself is untouched. */
    fun updateTarget(
        data: AppData,
        sessionExerciseId: String,
        plannedSets: Int? = null,
        repLow: Int? = null,
        repHigh: Int? = null,
        restSeconds: Int? = null,
    ): AppData = mutateExercise(data, sessionExerciseId) { ex ->
        ex.copy(
            plannedSets = (plannedSets ?: ex.plannedSets).coerceIn(0, 50),
            repLow = (repLow ?: ex.repLow).coerceIn(0, 500),
            repHigh = (repHigh ?: ex.repHigh).coerceIn(0, 500),
            restSeconds = (restSeconds ?: ex.restSeconds).coerceIn(0, 3600),
        )
    }

    fun addExerciseToSession(
        data: AppData,
        exerciseId: String,
        sets: Int = 3,
        repLow: Int = 8,
        repHigh: Int = 12,
        restSeconds: Int = 90,
    ): AppData = mutateActive(data) { session ->
        val nextLetter = ('A' + session.exercises.map { it.groupLetter }.distinct().size)
            .coerceAtMost('Z')
        session.copy(
            exercises = session.exercises + SessionExercise(
                exerciseId = exerciseId,
                group = "${nextLetter}1",
                plannedSets = sets,
                repLow = repLow,
                repHigh = repHigh,
                restSeconds = restSeconds,
                sets = prefilledSets(data, exerciseId, sets, repLow, null),
            ),
        )
    }

    fun removeExerciseFromSession(data: AppData, sessionExerciseId: String): AppData =
        mutateActive(data) { session ->
            session.copy(exercises = session.exercises.filterNot { it.id == sessionExerciseId })
        }

    /**
     * Swaps the movement while keeping the prescription and position.
     *
     * The machine being occupied is the commonest reason a session deviates from plan, and it
     * should cost one tap -- not a delete, a search and a re-entry of sets and reps.
     */
    fun replaceExercise(data: AppData, sessionExerciseId: String, newExerciseId: String): AppData =
        mutateExercise(data, sessionExerciseId) { ex ->
            ex.copy(
                exerciseId = newExerciseId,
                sets = ex.sets.map { if (it.done) it else it.copy(weightKg = 0.0) },
            )
        }

    fun moveExercise(data: AppData, from: Int, to: Int): AppData = mutateActive(data) { session ->
        val list = session.exercises.toMutableList()
        if (from !in list.indices || to !in list.indices) return@mutateActive session
        list.add(to, list.removeAt(from))
        session.copy(exercises = list)
    }

    // -- catalogue -------------------------------------------------------------------------------

    fun addExercise(data: AppData, exercise: Exercise): AppData =
        data.copy(exercises = data.exercises + exercise.copy(custom = true))

    fun updateExercise(data: AppData, exercise: Exercise): AppData =
        data.copy(exercises = data.exercises.map { if (it.id == exercise.id) exercise else it })

    /**
     * Archives rather than deletes.
     *
     * Sessions reference exercises by id. Removing one outright would leave every past
     * performance of it pointing at nothing, so it is hidden from pickers and kept for history.
     */
    fun archiveExercise(data: AppData, exerciseId: String, archived: Boolean = true): AppData =
        data.copy(exercises = data.exercises.map { if (it.id == exerciseId) it.copy(archived = archived) else it })

    // -- programme -------------------------------------------------------------------------------

    private fun mutateProgramme(data: AppData, programmeId: String, block: (Programme) -> Programme): AppData =
        data.copy(programmes = data.programmes.map { if (it.id == programmeId) block(it) else it })

    fun addProgramme(data: AppData, programme: Programme): AppData =
        data.copy(programmes = data.programmes + programme)

    fun updateProgramme(data: AppData, programme: Programme): AppData =
        data.copy(programmes = data.programmes.map { if (it.id == programme.id) programme else it })

    /** Exactly one programme is active at a time, so activating one deactivates the rest. */
    fun activateProgramme(data: AppData, programmeId: String): AppData =
        data.copy(programmes = data.programmes.map { it.copy(active = it.id == programmeId) })

    fun deleteProgramme(data: AppData, programmeId: String): AppData =
        data.copy(programmes = data.programmes.filterNot { it.id == programmeId })

    fun addRoutine(data: AppData, programmeId: String, routine: Routine): AppData =
        mutateProgramme(data, programmeId) { it.copy(routines = it.routines + routine) }

    fun updateRoutine(data: AppData, programmeId: String, routine: Routine): AppData =
        mutateProgramme(data, programmeId) { programme ->
            programme.copy(routines = programme.routines.map { if (it.id == routine.id) routine else it })
        }

    fun deleteRoutine(data: AppData, programmeId: String, routineId: String): AppData =
        mutateProgramme(data, programmeId) { programme ->
            programme.copy(routines = programme.routines.filterNot { it.id == routineId })
        }

    private fun mutateRoutine(
        data: AppData,
        programmeId: String,
        routineId: String,
        block: (Routine) -> Routine,
    ): AppData = mutateProgramme(data, programmeId) { programme ->
        programme.copy(routines = programme.routines.map { if (it.id == routineId) block(it) else it })
    }

    fun addPlanItem(data: AppData, programmeId: String, routineId: String, item: PlanItem): AppData =
        mutateRoutine(data, programmeId, routineId) { routine ->
            val letter = ('A' + routine.items.map { it.groupLetter }.distinct().size).coerceAtMost('Z')
            routine.copy(items = routine.items + item.copy(group = item.group.ifBlank { "${letter}1" }))
        }

    fun updatePlanItem(data: AppData, programmeId: String, routineId: String, item: PlanItem): AppData =
        mutateRoutine(data, programmeId, routineId) { routine ->
            routine.copy(items = routine.items.map { if (it.id == item.id) item else it })
        }

    fun removePlanItem(data: AppData, programmeId: String, routineId: String, itemId: String): AppData =
        mutateRoutine(data, programmeId, routineId) { routine ->
            routine.copy(items = routine.items.filterNot { it.id == itemId })
        }

    fun movePlanItem(data: AppData, programmeId: String, routineId: String, from: Int, to: Int): AppData =
        mutateRoutine(data, programmeId, routineId) { routine ->
            val list = routine.items.toMutableList()
            if (from !in list.indices || to !in list.indices) return@mutateRoutine routine
            list.add(to, list.removeAt(from))
            routine.copy(items = list)
        }

    // -- body --------------------------------------------------------------------------------------

    /**
     * One entry per date. A second check-in on the same day corrects the first rather than
     * creating a duplicate that would double-count in every trend.
     */
    fun saveBodyEntry(data: AppData, entry: BodyEntry): AppData {
        val existing = data.body.firstOrNull { it.date == entry.date && it.id != entry.id }
        val cleaned = if (existing != null) data.body.filterNot { it.id == existing.id } else data.body
        return if (cleaned.any { it.id == entry.id }) {
            data.copy(body = cleaned.map { if (it.id == entry.id) entry else it })
        } else {
            data.copy(body = cleaned + entry)
        }
    }

    fun deleteBodyEntry(data: AppData, entryId: String): AppData =
        data.copy(body = data.body.filterNot { it.id == entryId })

    // -- history -----------------------------------------------------------------------------------

    fun deleteSession(data: AppData, sessionId: String): AppData =
        data.copy(sessions = data.sessions.filterNot { it.id == sessionId })

    fun updateSession(data: AppData, session: Session): AppData =
        data.copy(sessions = data.sessions.map { if (it.id == session.id) session else it })

    // -- settings ------------------------------------------------------------------------------------

    fun updatePrefs(data: AppData, prefs: Prefs): AppData = data.copy(prefs = prefs)

    fun updateGoals(data: AppData, goals: Goals): AppData = data.copy(goals = goals)
}

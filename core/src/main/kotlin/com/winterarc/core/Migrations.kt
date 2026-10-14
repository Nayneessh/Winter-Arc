package com.winterarc.core

/**
 * Upgrades applied to a document that already exists.
 *
 * The seed is only ever read on a first run, so changing it reaches nobody who already has data.
 * Anything that must land on a device in use has to come through here instead.
 *
 * The contract every migration keeps, without exception:
 *
 *   - Training is never touched. Not a session, not a set, not a rep.
 *   - Nothing in the catalogue is removed or rewritten, including movements the user added.
 *   - Body check-ins, goals and preferences are left exactly as they are.
 *   - A programme is retired by being deactivated, never deleted, so sessions performed under it
 *     still resolve the routine they were performed under.
 *
 * Each runs exactly once, recorded by name, so it cannot later override a choice the user makes.
 */
object Migrations {

    const val WINTER_ARC_V41 = "winter-arc-v4-1"

    fun apply(data: AppData): AppData = installWinterArcV41(data)

    /**
     * Brings in the v4.1 programme and makes it the one in use.
     *
     * The previous programme is deactivated rather than removed: every session already performed
     * carries the id of the routine it came from, and deleting it would leave that pointing at
     * nothing. It disappears from view, which is what retiring a plan means, without any history
     * losing its provenance.
     */
    private fun installWinterArcV41(data: AppData): AppData {
        if (WINTER_ARC_V41 in data.appliedMigrations) return data

        // Only movements genuinely new to this plan. Anything the old plan already had is reused
        // by id, so its history, records and "last time" prefill carry straight over.
        val known = data.exercises.map { it.id }.toSet()
        val added = Programme41.newExercises.filterNot { it.id in known }

        val programmes = if (data.programmes.any { it.id == Programme41.ID }) {
            data.programmes
        } else {
            data.programmes + Programme41.programme
        }

        return data.copy(
            exercises = data.exercises + added,
            programmes = programmes.map { it.copy(active = it.id == Programme41.ID) },
            appliedMigrations = data.appliedMigrations + WINTER_ARC_V41,
        )
    }
}

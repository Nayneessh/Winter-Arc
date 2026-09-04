package com.winterarc.app.data.repo

import com.winterarc.domain.model.BodyMetric
import com.winterarc.domain.seed.WinterArcSeed
import java.time.LocalDate
import java.util.UUID

/**
 * First-run content.
 *
 * Runs exactly once, guarded by a programme count of zero. After that the seed constants are
 * never read again — the user's edits are the source of truth and re-seeding would overwrite
 * them. Every id below is deterministic (taken from the seed) so a repeat run is idempotent
 * even if the guard were somehow bypassed.
 */
class DatabaseSeeder(private val repo: TrainingRepository) {

    suspend fun seedIfEmpty(): Boolean {
        if (repo.activeProgramme() != null) return false
        if (repo.allExercises().isNotEmpty()) return false

        WinterArcSeed.exercises.forEach { repo.saveExercise(it) }
        WinterArcSeed.programmes.forEach { repo.saveProgramme(it) }

        // The stated current measurements, entered as a single baseline check-in.
        if (repo.allBodyMetrics().isEmpty()) {
            repo.saveBodyMetric(
                BodyMetric(
                    id = UUID.randomUUID().toString(),
                    date = LocalDate.now(),
                    weightKg = WinterArcSeed.Baseline.WEIGHT_KG,
                    chestCm = WinterArcSeed.Baseline.CHEST_CM,
                    waistCm = WinterArcSeed.Baseline.WAIST_CM,
                    bicepsLeftCm = WinterArcSeed.Baseline.BICEPS_CM,
                    bicepsRightCm = WinterArcSeed.Baseline.BICEPS_CM,
                    notes = "Baseline",
                ),
            )
        }
        return true
    }
}

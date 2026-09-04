package com.winterarc.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winterarc.app.ui.kit.GhostButton
import com.winterarc.app.ui.kit.SheetTitle
import com.winterarc.app.ui.kit.WinterField
import com.winterarc.app.ui.kit.WinterSheet
import com.winterarc.app.ui.theme.W
import com.winterarc.core.AppData
import com.winterarc.core.Exercise
import com.winterarc.core.Muscle

/**
 * Choosing a movement.
 *
 * Search plus a muscle filter, with movements already trained floated to the top: the thing
 * being looked for is nearly always something done before, and a strict alphabetical list buries
 * it under everything else in the catalogue.
 */
@Composable
fun ExercisePickerSheet(
    data: AppData,
    title: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }

    val familiar = remember(data.sessions) {
        data.sessions.filter { it.finished }
            .sortedByDescending { it.date }
            .flatMap { session -> session.exercises.map { it.exerciseId } }
            .distinct()
    }

    val results = remember(query, muscle, data.exercises, familiar) {
        data.exercises
            .filterNot { it.archived }
            .filter { muscle == null || it.muscle == muscle }
            .filter {
                query.isBlank() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.detail.contains(query, ignoreCase = true) ||
                    it.muscle.display.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareBy(
                    { familiar.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE },
                    { it.name },
                ),
            )
    }

    WinterSheet(onDismiss = onDismiss) {
        SheetTitle(title)
        WinterField(
            value = query,
            onValueChange = { query = it },
            label = "Search movements",
            keyboardType = KeyboardType.Text,
        )
        Spacer(Modifier.height(12.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            item {
                FilterChip("All", muscle == null) { muscle = null }
            }
            items(Muscle.entries.toList(), key = { it.name }) { entry ->
                FilterChip(entry.display, muscle == entry) {
                    muscle = if (muscle == entry) null else entry
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(results, key = { it.id }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    trained = exercise.id in familiar,
                    onClick = { onPick(exercise.id) },
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Nothing matches \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = W.Faint,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            }
        }

        if (onCreateNew != null) {
            Spacer(Modifier.height(12.dp))
            GhostButton(
                "Create a new movement",
                onCreateNew,
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.Add,
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, trained: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (trained) W.Gold else W.Ghost),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = W.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    exercise.muscle.display,
                    exercise.detail.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = W.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (exercise.custom) {
            Text("YOURS", style = MaterialTheme.typography.labelSmall, color = W.Ghost)
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) W.Void else W.Muted,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) W.Gold else W.Void.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

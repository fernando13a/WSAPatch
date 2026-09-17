package com.ironmind.app.ui.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.domain.model.SetLog
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.components.LabeledValue
import com.ironmind.app.ui.components.SectionTitle
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val restRemaining by viewModel.restRemaining.collectAsStateWithLifecycle()

    // Keep the screen awake during a workout.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Buzz + beep when the rest countdown finishes.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.restFinished.collect { playRestAlert(context) }
    }

    // Live session timer (ticks every second once the session has started).
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val elapsedSeconds = if (state.startedAt > 0) ((nowMillis - state.startedAt) / 1_000).toInt() else 0

    var editingSet by remember { mutableStateOf<SetLog?>(null) }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(state.title, color = Gold, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Cyan)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.finishSession(onBack) }) {
                        Text("Finalizar", color = Cyan, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GlassCard {
                    val totalSets = state.exerciseBlocks.sumOf { it.sets.size }
                    LabeledValue("Tiempo", formatMmSs(elapsedSeconds))
                    Spacer(Modifier.height(6.dp))
                    LabeledValue("Volumen total", "${state.totalVolume.toInt()} kg")
                    Spacer(Modifier.height(6.dp))
                    LabeledValue("Sets registrados", "$totalSets")
                }
            }

            item { RestTimerCard(restRemaining = restRemaining, onStart = viewModel::startRest, onStop = viewModel::stopRest) }

            item {
                AddSetCard(
                    exercises = state.availableExercises.map { it.id to it.name },
                    onAddSet = { exerciseId, weight, reps, notes ->
                        viewModel.addSet(exerciseId, weight, reps, notes)
                    },
                )
            }

            item { SectionTitle("Ejercicios") }

            items(state.exerciseBlocks, key = { it.exerciseId }) { block ->
                ExerciseBlockCard(
                    block = block,
                    onEditSet = { editingSet = it },
                    onDeleteSet = viewModel::deleteSet,
                )
            }
        }
    }

    editingSet?.let { set ->
        EditSetDialog(
            set = set,
            onDismiss = { editingSet = null },
            onSave = { updated ->
                viewModel.updateSet(updated)
                editingSet = null
            },
        )
    }
}

@Composable
private fun RestTimerCard(restRemaining: Int, onStart: (Int) -> Unit, onStop: () -> Unit) {
    GlassCard {
        SectionTitle("Descanso", accent = Cyan)
        Spacer(Modifier.height(12.dp))
        Text(
            text = formatMmSs(restRemaining),
            color = if (restRemaining > 0) Cyan else TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 90, 120).forEach { seconds ->
                OutlinedButton(onClick = { onStart(seconds) }) { Text("${seconds}s", color = Gold) }
            }
            if (restRemaining > 0) {
                TextButton(onClick = onStop) { Text("Detener", color = TextMuted) }
            }
        }
    }
}

@Composable
private fun AddSetCard(
    exercises: List<Pair<Long, String>>,
    onAddSet: (exerciseId: Long, weightKg: Double, reps: Int, notes: String?) -> Unit,
) {
    var selectedId by remember { mutableLongStateOf(0L) }
    var expanded by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Default the selector to the first available exercise once loaded.
    LaunchedEffect(exercises) {
        if (selectedId == 0L && exercises.isNotEmpty()) selectedId = exercises.first().first
    }
    val selectedName = exercises.firstOrNull { it.first == selectedId }?.second ?: "Selecciona ejercicio"

    GlassCard {
        SectionTitle("Registrar set", accent = Gold)
        Spacer(Modifier.height(12.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedName, color = Cyan)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                exercises.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        selectedId = id
                        expanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Peso (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                label = { Text("Reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notas (suplementos, energía…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        val weightValue = weight.toDoubleOrNull()
        val repsValue = reps.toIntOrNull()
        AccentButton(
            text = "Registrar set",
            enabled = selectedId != 0L && weightValue != null && repsValue != null,
            onClick = {
                if (weightValue != null && repsValue != null) {
                    onAddSet(selectedId, weightValue, repsValue, notes)
                    weight = ""
                    reps = ""
                    notes = ""
                }
            },
        )
    }
}

@Composable
private fun ExerciseBlockCard(
    block: ExerciseBlockUi,
    onEditSet: (SetLog) -> Unit,
    onDeleteSet: (SetLog) -> Unit,
) {
    GlassCard {
        Text(block.exerciseName, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (block.sets.isEmpty()) {
            Text("Sin sets todavía.", color = TextMuted)
        } else {
            block.sets.forEach { set ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Set ${set.setNumber}:  ${set.weightKg.toInt()} kg × ${set.reps}",
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditSet(set) },
                    )
                    TextButton(onClick = { onEditSet(set) }) { Text("Editar", color = Cyan) }
                    TextButton(onClick = { onDeleteSet(set) }) { Text("✕", color = TextMuted) }
                }
                if (!set.notes.isNullOrBlank()) {
                    Text("  ${set.notes}", color = TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun EditSetDialog(
    set: SetLog,
    onDismiss: () -> Unit,
    onSave: (SetLog) -> Unit,
) {
    var weight by remember { mutableStateOf(set.weightKg.toString()) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var notes by remember { mutableStateOf(set.notes ?: "") }
    val weightValue = weight.toDoubleOrNull()
    val repsValue = reps.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = weightValue != null && repsValue != null,
                onClick = {
                    if (weightValue != null && repsValue != null) {
                        onSave(set.copy(weightKg = weightValue, reps = repsValue, notes = notes.takeIf { it.isNotBlank() }))
                    }
                },
            ) { Text("Guardar", color = Cyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMuted) } },
        title = { Text("Editar set ${set.setNumber}", color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** Short buzz + beep to signal the end of a rest period. Best-effort; ignores failures. */
private fun playRestAlert(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
    }
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
